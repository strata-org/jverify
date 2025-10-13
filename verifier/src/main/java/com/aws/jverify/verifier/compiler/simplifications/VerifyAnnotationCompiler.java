package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Verify;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.generated.TokenRange;
import com.aws.jverify.verifier.IntervalTree;
import com.aws.jverify.verifier.JavaMethodVerificationStatus;
import com.aws.jverify.verifier.compiler.PositionCalculator;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.frontend.JavaToDafnyCompiler;
import com.sun.tools.javac.code.AnnoConstruct;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

/**
 * Depends on MethodOrLoopContractCompiler
 */
public class VerifyAnnotationCompiler extends TreeScanner {
    private final JVerifyUtils jverifyUtils;
    private final VerifierOptions options;
    public final Set<Symbol.MethodSymbol> removedImplementations = new HashSet<>();
    public final Context context;
    private final JavaToDafnyCompiler javaToDafnyCompiler;
    private final Reporter reporter;
    private PositionCalculator positionCalculator;
    private final HashMap<URI, IntervalTree<Integer, JavaMethodVerificationStatus>> sourceFileToMethodIntervalTreeMap = new HashMap<>();

    public VerifyAnnotationCompiler(Context context) {
        context.put(VerifyAnnotationCompiler.class, this);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        shouldVerifies.push(context.get(VerifierOptions.class).verifyByDefault()
                ? ShouldVerifyMode.DefaultYes
                : ShouldVerifyMode.DefaultNo);
        options = context.get(VerifierOptions.class);
        this.context = context;
        javaToDafnyCompiler = context.get(JavaToDafnyCompiler.class);
    }
    
    public static VerifyAnnotationCompiler instance(Context context) {
        VerifyAnnotationCompiler instance = context.get(VerifyAnnotationCompiler.class);
        if (instance == null) {
            instance = new VerifyAnnotationCompiler(context);
        }
        return instance;
    }

    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            reporter.compilationUnit = env;
            visitTopLevel(env);
        }
        return envs;
    }

    public final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();

    public enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        reporter.compilationUnit = tree;
        processVerifyAnnotation(tree.packge);
        if (!javaToDafnyCompiler.isLibrary(reporter.compilationUnit)) {
            var sourceUri = getUri();
            if (!sourceFileToMethodIntervalTreeMap.containsKey(sourceUri)) {
                sourceFileToMethodIntervalTreeMap.put(sourceUri, new IntervalTree<>());
                positionCalculator = new PositionCalculator(tree);
            }
        }
        super.visitTopLevel(tree);
        shouldVerifies.pop();
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        processVerifyAnnotation(tree.sym);
        super.visitClassDef(tree);
        shouldVerifies.pop();
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        boolean shouldVerify = processVerifyAnnotationAndPop(tree, tree.sym);
        addMethodToIntervalTree(tree, shouldVerify);
        if (!shouldVerify) {
            removeImplementation(tree);
        } else if (!jverifyUtils.isPure(tree.sym) && !applyPositionFilter(tree)) {
            // this is a performance optimization. 
            // Dafny should apply the position filter as well, which will also work for pure methods, unlike this.
            removeImplementation(tree);
        }
        super.visitMethodDef(tree);
    }

    private void addMethodToIntervalTree(JCTree.JCMethodDecl methodDecl, boolean shouldVerify) {
        var sourceFileURI = getUri();
        if (!sourceFileToMethodIntervalTreeMap.containsKey(sourceFileURI) 
                || BaseDafnyGenerator.isSynthetic(methodDecl.getModifiers().flags)
                || !MethodOrLoopContractCompiler.hasImplementation(methodDecl)) {
           return;
        }

        var startPos = positionCalculator.toToken(positionCalculator.getStartPos(methodDecl));

        /*
          Note: setting end position to be start position in case there isn't any explicit end position
          This happens for implicit constructors where start position is the start position for the class declaration
         */
        var endPos = positionCalculator.toToken(positionCalculator.getEndPos(methodDecl)) != null ?
                positionCalculator.toToken(positionCalculator.getEndPos(methodDecl)) : startPos;


        var methodVerificationStatus = new JavaMethodVerificationStatus(methodDecl, new TokenRange(startPos,endPos), shouldVerify ?
                JavaMethodVerificationStatus.VerificationStatus.Verified :  JavaMethodVerificationStatus.VerificationStatus.Skipped);

        sourceFileToMethodIntervalTreeMap.get(sourceFileURI)
                .insert(methodVerificationStatus.getPosition().getStartToken().getLine(), methodVerificationStatus.getPosition().getEndToken().getLine(),
                        methodVerificationStatus);
    }

    private URI getUri() {
        var baseUri = Paths.get(System.getProperty("user.dir")).toUri();
        return baseUri.relativize(reporter.compilationUnit.getSourceFile().toUri());
    }

    public void removeImplementation(JCTree.JCMethodDecl tree) {
        if (tree.body == null || tree.body.getStatements().isEmpty()) {
            return;
        }
        var contractBlock = MethodOrLoopContractCompiler.getContractBlock(tree);
        tree.body.stats = List.of(contractBlock, jverifyUtils.contractThrow());
    }

    public boolean processVerifyAnnotationAndPop(JCTree node, AnnoConstruct annoConstruct) {
        processVerifyAnnotation(annoConstruct);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        return shouldVerify;
    }

    public boolean shouldVerify() {
        for (int i = shouldVerifies.size() - 1; i >= 0; i--) {
            var mode = shouldVerifies.get(i);
            if (mode == ShouldVerifyMode.AlwaysYes || mode == ShouldVerifyMode.DefaultYes) {
                return true;
            } else if (mode == ShouldVerifyMode.AlwaysNo || mode == ShouldVerifyMode.DefaultNo) {
                return false;
            }
        }
        throw new RuntimeException("shouldVerify should never be empty");
    }
    
    private boolean applyPositionFilter(JCTree.JCMethodDecl method) {
        var filter = options.positionFilter();
        if (filter == null) {
            return true;
        } else {
            if (filter.fileEnding() != null
                    && !reporter.compilationUnit.getSourceFile().getName().endsWith(filter.fileEnding())) {
                return false;
            }

            var nodeRange = Reporter.getRange(reporter.getName(method, method.name).getOrigin());
            int line = nodeRange.getStartToken().getLine();
            return filter.start() <= line && line <= filter.end();
        }
    }

    public void addShouldVerify(ShouldVerifyMode mode) {
        if (shouldVerifies.peek() == ShouldVerifyMode.AlwaysYes) {
            shouldVerifies.push(ShouldVerifyMode.AlwaysYes);
        } else if (shouldVerifies.peek() == ShouldVerifyMode.AlwaysNo) {
            shouldVerifies.push(ShouldVerifyMode.AlwaysNo);
        } else if (mode == ShouldVerifyMode.Inherit) {
            shouldVerifies.push(shouldVerifies.peek());
        } else {
            shouldVerifies.push(mode);
        }
    }
    
    public void processVerifyAnnotation(AnnoConstruct annoConstruct) {
        processVerifyAnnotation(annoConstruct.getAnnotation(Verify.class));
    }

    public void processVerifyAnnotation(@Nullable Verify verify) {
        if (verify != null) {
            var should = verify.value();
            var includeMembers = verify.overrideChildren();
            addShouldVerify(getVerifyMode(should, includeMembers));
        } else {
            addShouldVerify(ShouldVerifyMode.Inherit);
        }
    }

    public void processVerifyAnnotation(Map<String, JCTree.JCAnnotation> annotationsByName) {
        ShouldVerifyMode mode = getShouldVerifyMode(annotationsByName);
        addShouldVerify(mode);
    }

    public ShouldVerifyMode getShouldVerifyMode(Map<String, JCTree.JCAnnotation> annotationsByName) {
        ShouldVerifyMode mode;
        var verifyAnnotation = annotationsByName.get(Verify.class.getName());
        if (verifyAnnotation != null) {
            var arguments = BaseDafnyGenerator.getArguments(verifyAnnotation);
            var shouldArgument = arguments.get("value");
            var should = true;
            if (shouldArgument != null) {
                should = (boolean) BaseDafnyGenerator.getLiteralValue(shouldArgument);
            }

            var pushDownArgument = arguments.get("overrideChildren");
            var includeMembers = true;
            if (pushDownArgument != null) {
                includeMembers = (boolean) BaseDafnyGenerator.getLiteralValue(pushDownArgument);
            }
            mode = getVerifyMode(should, includeMembers);
        } else {
            mode = ShouldVerifyMode.Inherit;
        }
        return mode;
    }

    private ShouldVerifyMode getVerifyMode(boolean should, boolean pushDown) {
        if (should) {
            if (pushDown) {
                return ShouldVerifyMode.AlwaysYes;
            } else {
                return ShouldVerifyMode.DefaultYes;
            }
        } else {
            if (pushDown) {
                return ShouldVerifyMode.AlwaysNo;
            } else {
                return ShouldVerifyMode.DefaultNo;
            }
        }
    }

    public HashMap<URI, IntervalTree<Integer, JavaMethodVerificationStatus>> getSourceFileToMethodIntervalTreeMap() {
        return sourceFileToMethodIntervalTreeMap;
    }

}

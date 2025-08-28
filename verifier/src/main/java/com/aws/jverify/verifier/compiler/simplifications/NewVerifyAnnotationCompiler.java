package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Verify;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.tools.javac.code.AnnoConstruct;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Depends on the contract/implementation format for method bodies
 * that NewMethodOrLoopContractCompiler establishes
 */
public class NewVerifyAnnotationCompiler extends TreeScanner {
    private final JVerifyMaker jverifyMaker;

    public NewVerifyAnnotationCompiler(Context context) {
        context.put(NewVerifyAnnotationCompiler.class, this);
        jverifyMaker = JVerifyMaker.instance(context);
        shouldVerifies.push(context.get(VerifierOptions.class).verifyByDefault()
                ? ShouldVerifyMode.DefaultYes
                : ShouldVerifyMode.DefaultNo);
    }
    
    public static NewVerifyAnnotationCompiler instance(Context context) {
        NewVerifyAnnotationCompiler instance = context.get(NewVerifyAnnotationCompiler.class);
        if (instance == null) {
            instance = new NewVerifyAnnotationCompiler(context);
        }
        return instance;
    }

    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            visitTopLevel(env);
        }
        return envs;
    }

    public final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();

    public enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        processVerifyAnnotation(tree.packge);
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
        boolean shouldVerify = processVerifyAnnotationAndPop(tree.sym);
        if (!shouldVerify) {
            removeImplementation(tree);
        }
        super.visitMethodDef(tree);
    }

    public void removeImplementation(JCTree.JCMethodDecl tree) {
        var contractBlock = MethodOrLoopContractCompiler.getContractBlock(tree);
        tree.body.stats = List.of(contractBlock, jverifyMaker.contractThrow());
    }

    public boolean processVerifyAnnotationAndPop(AnnoConstruct annoConstruct) {
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
            var arguments = JavaToDafnyCompiler.getArguments(verifyAnnotation);
            var shouldArgument = arguments.get("value");
            var should = true;
            if (shouldArgument != null) {
                should = (boolean) JavaToDafnyCompiler.getLiteralValue(shouldArgument);
            }

            var pushDownArgument = arguments.get("overrideChildren");
            var includeMembers = true;
            if (pushDownArgument != null) {
                includeMembers = (boolean) JavaToDafnyCompiler.getLiteralValue(pushDownArgument);
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
}

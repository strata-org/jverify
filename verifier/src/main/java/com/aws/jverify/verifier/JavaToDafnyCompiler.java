package com.aws.jverify.verifier;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.sun.source.tree.*;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.comp.Enter;

import com.sun.tools.javac.tree.TreeInfo;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public final Context context;

    public final Set<Symbol.MethodSymbol> symbolsWithAContract = new HashSet<>();
    private final Stack<IOrigin> contextOrigins = new Stack<>();
    public final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    public final NameCompiler nameCompiler;
    private JCDiagnostic.Factory diagnosticFactory;

    /**
     * Edges are from child to parent types, similar to the references in the code
     */
    private final Graph<Symbol.ClassSymbol, DefaultEdge> typeHierarchy = new DefaultDirectedGraph<>(DefaultEdge.class);
    private final Map<Symbol.ClassSymbol, List<JCTree.JCClassDecl>> declarationsForSymbolContract = new HashMap<>();
    public Map<CompilationUnitTree, List<TopLevelDecl>> declarationsForFile = new HashMap<>();
    public final ExpressionCompiler expressionCompiler = new ExpressionCompiler(this);

    private final Map<Symbol.ClassSymbol, ExternalTypeContract> externalContracts = new HashMap<>();
    // All contracts, internal or external
    final Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts = new HashMap<>();
    JCTree.JCCompilationUnit compilationUnit;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;

    private final List<Symbol.MethodSymbol> invariants = new ArrayList<>();
    private final List<JCTree.JCVariableDecl> initializers = new ArrayList<>();

    public JavaToDafnyCompiler(Context context, VerifierOptions verifierOptions) {
        this.context = context;
        shouldVerifies.push(verifierOptions.verifyByDefault()
                ? ShouldVerifyMode.DefaultYes
                : ShouldVerifyMode.DefaultNo);
        nameCompiler = new NameCompiler(verifierOptions.avoidCollisionsUsingUnderscores());
    }

    public String getInitMethodName(String className, String constructorName) {
        return constructorName.replace("ctor", nameCompiler.INIT_METHOD_PREFIX) + "_" + className;
    }

    public NameCompiler getNameCompiler() {
        return nameCompiler;
    }
    
    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, List<JavaFileObject> files) {
        var parsed = parseResolveAndDesugarJava(options, files);
        if (parsed == null) {
            return new FilesContainer(List.of());
        }

        for (var compilationUnit : parsed) {
            discoverContractsAndTypeHierarchy((JCTree.JCCompilationUnit) compilationUnit);
            declarationsForFile.put(compilationUnit, new ArrayList<>());
        }
        for(var compiledClass : declarationsForSymbolContract.keySet()) {
            nameCompiler.registerClass(compiledClass);
        }
        compileSymbolsTopologically();

        List<FileStart> filesStarts = new ArrayList<>();
        for (var compilationUnit : parsed) {
            List<TopLevelDecl> fileDeclarations = declarationsForFile.get(compilationUnit);
            // fileDeclarations.sort(t -> ((SourceOrigin)t.getOrigin()).getEntireRange().getStartToken());
            filesStarts.add(new FileStart(compilationUnit.sourcefile.toUri().toString(), fileDeclarations));
        }

        return new FilesContainer(filesStarts);
    }

    /**
     * Applies a subset of the javac compilation pipeline, to parse,
     * resolve, and partially rewrite some features away.
     */
    private Iterable<JCTree.JCCompilationUnit> parseResolveAndDesugarJava(VerifierOptions options, List<JavaFileObject> files) {
        // don't assume the argument is modifiable
        files = new ArrayList<>(files);
        files.add(new SourceFile("builtin-contracts.java", Common.getResourceFile(getClass(), builtinFile)));

        for(var extraPath : options.extraClassPathEntries()) {
            if (!Files.exists(extraPath.toAbsolutePath())) {
                throw new IllegalArgumentException("Could not find file: " + extraPath);
            }
        }
        var classpathEntries = new ArrayList<Path>(options.extraClassPathEntries());
        var classpath = classpathEntries.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        var javacOptions = List.of("-cp", classpath);

        ClientCodeWrapper ccw = ClientCodeWrapper.instance(context);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        context.put(DiagnosticListener.class, ccw.wrap(diagnostics));
        JavaCompiler compiler = JavaCompiler.instance(context);
        Arguments args = Arguments.instance(context);
        args.init("javac", javacOptions, List.of(), files);

        /**
         * The javac phases are as follows (copied from CompileStates.CompileState):
         *
         * INIT(0),
         * PARSE(1),
         * ENTER(2),
         * PROCESS(3),
         * ATTR(4),
         * FLOW(5),
         * TRANSTYPES(6),
         * TRANSPATTERNS(7),
         * UNLAMBDA(8),
         * LOWER(9),
         * GENERATE(10);
         *
         * The first half mostly adds information to the tree, like resolution,
         * whereas the second half starts to be more destructive,
         * lowering higher-level features to lower-level ones.
         * Some of the latter are helpful, but in some cases the target language (Dafny)
         * supports features that JVM bytecode doesn't, so the phases don't help.
         *
         * Currently, we apply 0 through 5,
         * skip 6 and 7 as they remove features Dafny supports directly (generics and patterns),
         * but then apply 8 in order to rewrite lambda expressions and method references.
         * We may partially apply 9 in the future to rewrite features such as nested classes.
         * 10 actually generates JVM bytecode so we will likely never apply it.
         *
         * Because we have specification and proof code that use features like lambdas
         * for different purposes, we also apply our own phases before and after UNLAMBDA
         * in order to temporarily remove code we don't want rewritten and then restore it.
         * Therefore, our current pipeline looks like this:
         *
         * INIT(0),
         * PARSE(1),
         * ENTER(2),
         * PROCESS(3),
         * ATTR(4),
         * FLOW(5),
         * SUBSTITUTE,
         * UNLAMBDA(8),
         * UNSUBSTITUTE
         *
         * For practical reasons we also have to stop the normal flow of the JavaCompiler
         * after 3 in order to get a reference to the set of compilation targets
         * (via a TaskListener and a configuration to stop the pipeline early,
         * and the Todo instance in the context).
         */
        compiler.shouldStopPolicyIfNoError = CompileStates.CompileState.PROCESS;
        final Queue<Env<AttrContext>> envs = new LinkedList<>();
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.add(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                TaskListener.super.finished(e);

                // Wait for the last event sent, after all compilation is complete
                // (which will be just phase 0 through 3 because of the shouldStopPolicyIfNoError setting)
                if (e.getKind() == TaskEvent.Kind.COMPILATION) {
                    // The earlier phases leave the queue of classes to process
                    // in this instance.
                    // JavaCompile.compile() would normally make a call equivalent to
                    // generate(desugar(flow(attribute(todo)))),
                    // where desugar() applied phases 6 through 9.
                    Todo todo = Todo.instance(context);

                    // The stop policy has to be moved back further
                    // or else the later phases become no-ops.
                    compiler.shouldStopPolicyIfNoError = CompileStates.CompileState.FLOW;

                    // Apply the second half of our pipeline as above (4 and onwards).
                    // See the implementation of JavaCompiler.compile() for similar lines,
                    // including the comment "these method calls must be chained to avoid memory leaks"
                    envs.addAll(unsubstitute(unlambda(substitute(compiler.flow(compiler.attribute(todo))))));
                }
            }
        });
        // Applies the first half of our pipeline.
        compiler.compile(files, List.of(), null, List.of());

        this.diagnosticFactory = JCDiagnostic.Factory.instance(context);
        var hasErrors = false;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                JCDiagnostic.DiagnosticPosition position = new DiagnosticPositionFromDiagnostic(diagnostic);
                this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                        new DiagnosticSource(diagnostic.getSource(), null), position, "javaError",
                        diagnostic.getMessage(Locale.ENGLISH)));

                hasErrors = true;
            }
        }

        return hasErrors ? null : envs.stream().map(e -> e.toplevel).collect(Collectors.toSet());
    }

    // Phase to replace erased code such as specifications with placeholders
    // so future phases don't rewrite them.
    private Queue<Env<AttrContext>> substitute(Queue<Env<AttrContext>> envs) {
        var substituter = ErasedCodeSubstituter.instance(context);
        for (Env<AttrContext> env: envs) {
            env.tree = substituter.substitute(env.tree);
        }
        return envs;
    }

    // Phase to replace placeholders substituted by the earlier SUBSTITUTE phase
    // with their original AST nodes.
    private Queue<Env<AttrContext>> unsubstitute(Queue<Env<AttrContext>> envs) {
        var substituter = ErasedCodeSubstituter.instance(context);
        for (Env<AttrContext> env : envs) {
            env.tree = substituter.unsubstitute(env.tree);
        }
        return envs;
    }

    private Queue<Env<AttrContext>> unlambda(Queue<Env<AttrContext>> envs) {
        TreeMaker localMake = TreeMaker.instance(context).at(Position.NOPOS);

        // Note JavaCompiler.desugar has some additional logic to
        // scan for classes that have lambdas first,
        // to not waste time with these traversals.
        // We could do the same here to save time in the future.
        for (Env<AttrContext> env : envs) {
            env.tree = LambdaToMethod.instance(context).translateTopLevelClass(env, env.tree, localMake);
        }
        return envs;
    }

    private void compileSymbolsTopologically() {
        var iterator = new TopologicalOrderIterator<>(this.typeHierarchy);
        var itemsFromChildrenToParents = new ArrayList<Symbol.ClassSymbol>();
        iterator.forEachRemaining(itemsFromChildrenToParents::add);
        for(var currentTypeSymbol : itemsFromChildrenToParents.reversed()) {
            var relatedDeclarations = declarationsForSymbolContract.get(currentTypeSymbol);
            if (relatedDeclarations == null) {
                continue;
            }
            for(var relatedDeclaration : relatedDeclarations) {
                Enter enter = Enter.instance(context);
                Env<AttrContext> env = enter.getEnv(relatedDeclaration.sym);
                if (env != null) {
                    compilationUnit = env.toplevel;
                }
                var dafnyDecls = translateTypeDeclaration(relatedDeclaration);
                declarationsForFile.get(compilationUnit).addAll(dafnyDecls);
            }
        }
    }

    record ExternalTypeContract(Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts) { }
    private void discoverContractsAndTypeHierarchy(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        var typesToVisit = new LinkedList<>(compilationUnit.getTypeDecls());
        while(!typesToVisit.isEmpty()) {
            var typeDecl = typesToVisit.poll();
            if (!(typeDecl instanceof JCTree.JCClassDecl classDecl)) {
                continue;
            }
            
            var classAnnotations = classDecl.getModifiers().getAnnotations();
            var classAnnotationsByName = classAnnotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));
            var contractAnnotation = classAnnotationsByName.get(Contract.class.getName());

            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCClassDecl nestedClass) {
                    typesToVisit.push(nestedClass);
                }
            }
            if (contractAnnotation == null) {
                var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(classDecl.sym, (_) -> new ArrayList<>());
                declsForSymbol.add(classDecl);
                addHierarchyForSymbol(classDecl.sym);
                continue;
            }
            
            var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
            if (contracteeSymbol == null) {
                reportError(classDecl, "noContractTarget", classDecl.name.toString());
                continue;
            }

            var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(contracteeSymbol, (_) -> new ArrayList<>());
            declsForSymbol.add(classDecl);

            addHierarchyForSymbol(contracteeSymbol);
            if (externalContracts.containsKey(contracteeSymbol)) {
                reportError(contractAnnotation, "duplicateContract", contracteeSymbol.name);
                continue;
            }
            if (typeHasSource(contracteeSymbol) && !isInterfaceOrAbstract(contracteeSymbol)) {
                reportError(contractAnnotation, "concreteTypeWithExternalContract", contracteeSymbol.name);
                continue;
            }
            
            Map<Symbol.MethodSymbol, MethodOrLoopContract> externalContracts = new HashMap<>();
            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCClassDecl nestedClass) {
                    typesToVisit.push(nestedClass);
                }
                
                if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                    continue;
                }

                var methodSymbol = methodDecl.sym;
                var baseMethod = OverrideFinder.findOverriddenMethod(methodSymbol, Types.instance(context));
                if (baseMethod != null) {
                    var header = extractContract(methodDecl, true);
                    externalContracts.put(baseMethod, header);
                    methodContracts.put(baseMethod, header);
                } else if (!isSynthetic(methodDecl, methodSymbol)) {
                    // Check currently does not take into account overloading
                    // But this only makes it not detect some unused methods.
                    var contractee = StreamSupport.stream(contracteeSymbol.members().getSymbolsByName(methodSymbol.name).spliterator(), false).toList();
                    if (contractee.isEmpty()) {
                        reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                    }
                }
            }
            this.externalContracts.put(contracteeSymbol, new ExternalTypeContract(externalContracts));
        }
    }

    private MethodOrLoopContract extractContract(JCTree.JCMethodDecl methodDecl, boolean reportErrors) {
        var methodAnnotations = methodDecl.getModifiers().getAnnotations();
        var methodAnnotationsByName = methodAnnotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));

        var methodCompiler = new BlockCompiler(this);
        var isPure = methodAnnotationsByName.containsKey(Pure.class.getName());
        var header = new MethodOrLoopContract(methodDecl, isPure);
        if (methodDecl.getBody() != null) {
            methodCompiler.translateHeader(methodDecl.getBody(), header, reportErrors);
        }

        return header;
    }

    private void addHierarchyForSymbol(Symbol.ClassSymbol sym) {
        typeHierarchy.addVertex(sym);
        for(var base : sym.getInterfaces()) {
            if (base.tsym instanceof Symbol.ClassSymbol classBase) {
                typeHierarchy.addVertex(classBase);
                typeHierarchy.addEdge(sym, classBase);
            }
        }
        if (sym.getSuperclass().tsym instanceof Symbol.ClassSymbol baseClass) {
            typeHierarchy.addVertex(baseClass);
            typeHierarchy.addEdge(sym, baseClass);
        }
    }

    private String methodToString(JCTree tree) {
        if (tree instanceof JCTree.JCMethodDecl methodDecl){
            if (isConstructor(methodDecl.sym)) {
                return "constructor";
            } else {
                return "method '" + methodDecl.name + "'";
            }
        } else {
            return "lambda";
        }
    }

    private Symbol.ClassSymbol getContractTarget(JCTree.JCClassDecl classDecl, JCTree.JCAnnotation contractAnnotation) {
        if (contractAnnotation == null) {
            return null;
        }
        
        var arguments = getArguments(contractAnnotation);
        var symbol = getClassSymbol(arguments.get("value"));
        if (symbol == null || symbol.getQualifiedName().contentEquals("com.aws.jverify.Contract")) {
            var superClass = classDecl.sym.getSuperclass();
            if (classDecl.extending != null && superClass != null) {
                return (Symbol.ClassSymbol) superClass.tsym;
            }
            var interfaces = classDecl.sym.getInterfaces();
            if (interfaces.isEmpty()) {
                return null;
            } 
            return (Symbol.ClassSymbol) interfaces.getFirst().tsym;
        }
        return symbol;
    }

    private static Symbol.ClassSymbol getClassSymbol(JCTree.JCExpression valueArgument) {
        if (valueArgument == null) {
            return null;
        }
        
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess &&
            fieldAccess.selected instanceof JCTree.JCIdent ident &&
            ident.sym instanceof Symbol.ClassSymbol classSymbol)
        {
            return classSymbol;
        } else {
            throw new JavaViolationException();
        }
    }

    private void reportError(IOrigin origin, String key, Object... args) {
        reportError(positionFromOrigin(origin), key, args);
    }

    private JCDiagnostic.DiagnosticPosition positionFromOrigin(IOrigin origin) {
        return new DiagnosticPositionFromOrigin(originToRange(origin), compilationUnit.lineMap);
    }

    public void reportError(JCTree tree, String key, Object... args) {
        reportError(positionFromNode(tree, compilationUnit), key, args);
    }
    
    private void reportError(JCDiagnostic.DiagnosticPosition position, String key, Object... args) {
        this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                new DiagnosticSource(compilationUnit.getSourceFile(), null), position, key,
                args));
    }
    
    enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }
    private final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();
    private boolean shouldVerify() {
        if (typeForWhichCurrentClassIsDefiningContract != null) {
            // Do not verify methodContracts in the contracter.
            return false;
        }
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
    
    private void addShouldVerify(ShouldVerifyMode mode) {
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
    
    Map<String, JCTree.JCExpression> getArguments(JCTree.JCAnnotation annotation) {
        var result = new HashMap<String, JCTree.JCExpression>();
        for(var argument : annotation.getArguments()) {
            if (argument instanceof JCTree.JCAssign assign &&
                    assign.lhs instanceof JCTree.JCIdent ident) {
                result.put(ident.name.toString(), assign.rhs);
            } else {
                throw new JavaViolationException();
            }
        }
        return result;
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
    
    List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {

            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));

            processVerifyAnnotation(annotationsByName);


            Name name = null;
            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                var contractee = getContractTarget(classDecl, contractAnnotation);
                if (contractee != null) {
                    
                    if (typeHasSource(contractee)) {
                        // If the contractee has source, then we have a merged contract
                        // And we do not need to traverse the contracter.
                        // The contractee will lookup contracts in the contractor
                        // for bodyless members
                        var modifiableAnnotation = annotationsByName.get(Modifiable.class.getName());
                        if (modifiableAnnotation != null) {
                            reportError(modifiableAnnotation, "annotationOnSourceContractClass", Modifiable.class.getSimpleName(), classDecl.name.toString());
                        }
                        return List.of();
                    }
                    
                    
                    typeForWhichCurrentClassIsDefiningContract = contractee;
                    name = getName(classDecl, typeForWhichCurrentClassIsDefiningContract);
                }
            } else {
                for(var member : classDecl.getMembers()) {
                    if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                        continue;
                    }
                    // Don't report errors when extracting this contract here,
                    // since the actual translation of the method will report them.
                    var header = extractContract(methodDecl, false);
                    methodContracts.put(methodDecl.sym, header);
                }
            }

            if (name == null) {
                name = getName(classDecl, classDecl.sym);
            }
            var origin = declToOrigin(classDecl, name);
            contextOrigins.push(origin);

            List<TopLevelDecl> result = switch (classDecl.getKind()) {
                case ENUM -> List.of(translateEnum(classDecl, origin, name));
                case INTERFACE, CLASS -> translateClass(classDecl, origin, name)
                        .stream()
                        .map(decl -> (TopLevelDecl) decl)
                        .toList();
                case RECORD -> List.of(translateRecord(classDecl, origin, name));
                case ANNOTATION_TYPE -> {
                    reportError(classDecl, "notSupported", "%s declaration".formatted(classDecl.getKind()));
                    yield List.of();
                }
                // JCClassDecl#getKind returns one of the above five values
                default -> throw new JavaViolationException("unexpected kind: " + classDecl.getKind());
            };
            typeForWhichCurrentClassIsDefiningContract = null;
            contextOrigins.pop();
            shouldVerifies.pop();
            return result;
        }
        if (tree instanceof JCTree jcTree) {
            reportError(jcTree, "notSupported", "type declaration " + tree.getClass().getSimpleName());
            return List.of();
        } else {
            throw new NotImplementedException(tree.getClass().getName());
        }
    }

    private static boolean isInterface(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.INTERFACE) != 0;
    }

    private static boolean isAbstract(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.ABSTRACT) != 0;
    }

    private static boolean isInterfaceOrAbstract(Symbol.ClassSymbol classDecl) {
        return isInterface(classDecl) || isAbstract(classDecl);
    }
    
    private void processVerifyAnnotation(Map<String, JCTree.JCAnnotation> annotationsByName) {
        var verifyAnnotation = annotationsByName.get(Verify.class.getName());
        if (verifyAnnotation != null) {
            var arguments = getArguments(verifyAnnotation);
            var shouldArgument = arguments.get("value");
            var should = true;
            if (shouldArgument != null) {
                should = (boolean) getLiteralValue(shouldArgument);
            }

            var pushDownArgument = arguments.get("overrideChildren");
            var includeMembers = true;
            if (pushDownArgument != null) {
                includeMembers = (boolean) getLiteralValue(pushDownArgument);
            }
            addShouldVerify(getVerifyMode(should, includeMembers));
        } else {
            addShouldVerify(ShouldVerifyMode.Inherit);
        }
    }

    private static Object getLiteralValue(JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCLiteral literal) {
            return literal.getValue();
        } else {
            throw new JavaViolationException();
        }
    }

    private boolean isNestedClass(JCTree.JCClassDecl classDecl) {
        if (classDecl.sym != null && classDecl.sym.owner != null) {
            return classDecl.sym.owner.kind == Kinds.Kind.TYP;
        }

        return false;
    }
    
    private List<ClassLikeDecl> translateClass(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        invariants.clear();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (methodDecl.getModifiers().getAnnotations().stream().
                        anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident && 
                                ident.name.contentEquals("Invariant"))) {
                    invariants.add(methodDecl.sym);
                }
            }
        }
        
        ArrayList<MemberDecl> members = new ArrayList<>();
        initializers.clear();
        // First translate all fields and store default initializers to add to constructors
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var dafnyMember = translateField(variableDecl);
                if (dafnyMember != null) {
                    members.add(dafnyMember);
                }
            }

        }
        // Now translate other members
        for (var member : classDecl.getMembers()) {
            if (!(member instanceof JCTree.JCVariableDecl)) {
                var dafnyMember = translateMember(member);
                if (dafnyMember != null) {
                    members.add(dafnyMember);
                }
            }
        }
        // TODO: Check for existing equals()
        var symtab = Symtab.instance(context);
        var objectName = nameCompiler.getCompiledName(symtab.objectType.tsym);
        var otherIn = new Formal(origin, new Name(origin, "other"), new UserDefinedType(origin, new NameSegment(origin, objectName, null)),
                false, true, null, null, false, false, false, null);
        var equalsFunction = new Function(origin, new Name(origin, "equals"), null, false, null, List.of(),
                List.of(otherIn),
                List.of(), List.of(), new Specification<>(List.of(), null),
                new Specification<>(List.of(), null), false, false, null, new BoolType(origin),
                null, null, null);
        members.add(equalsFunction);

        var definingSymbol = getCurrentTypeSymbol(classDecl);
        
        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null) {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }
        var superTraits = baseTypes.
                filter(this::typeHasAContract).
                map((com.sun.tools.javac.code.Type type) -> translateType(null, type, origin)).
                collect(Collectors.<Type>toList());
        
        var typeParameters = translateTypeParameters(classDecl.typarams);
        return buildTraitAndClassTwin(classDecl, origin, name, members, typeParameters, superTraits);
    }

    /**
     * Translating Java classes to both a Dafny trait and a class is used to support classes extending classes
     */
    private List<ClassLikeDecl> buildTraitAndClassTwin(
            JCTree.JCClassDecl classDecl, IOrigin origin, Name name,
            ArrayList<MemberDecl> members, List<TypeParameter> typeParameters, List<Type> superTraits) {
        var traitMembers = new ArrayList<MemberDecl>();
        var classMembers = new ArrayList<MemberDecl>();
        var definingSymbol = getCurrentTypeSymbol(classDecl);
        var classNeeded = !isInterfaceOrAbstract(definingSymbol);
        for(var member : members) {
            switch (member) {
                case Method method when !method.getHasStaticKeyword() -> {
                    if (method.getBody() == null) {
                        classMembers.add(member);
                    }
                    traitMembers.add(member);
                }
                case Function function -> {
                    if (name.getValue().equals("java_lang_Object") || !function.getNameNode().getValue().equals("equals")) {
                        traitMembers.add(function);
                    }
                    if (function.getBody() == null) {
                        classMembers.add(member);
                    }
                }
                case Constructor constructor -> {
                    classNeeded = true;
                    Method initMethod = constructorToInitMethod(name.getValue(), constructor);
                    if (initMethod != null) {
                        traitMembers.add(initMethod);
                    }

                    var classConstructor = new Constructor(constructor.getOrigin(), constructor.getNameNode(), null, false, null,
                            constructor.getTypeArgs(), constructor.getIns(),
                            constructor.getReq(), constructor.getEns(), constructor.getReads(),
                            constructor.getDecreases(), constructor.getMod(),
                            null);
                    classMembers.add(classConstructor);
                }
                case null, default -> traitMembers.add(member);
            }
        }

        // We use Object as the top type even for types we translate to Dafny value types, such as String and records,
        // so we don't want to put `extends object` on the Object trait.
        if (!(isInterface(definingSymbol) || definingSymbol.className().equals("java.lang.Object"))
            || isAnnotated(classDecl.type, Modifiable.class)) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "object", null)));
        }

        var trait = new TraitDecl(origin, name, null, typeParameters, traitMembers, superTraits, false);
        List<Type> typeArgs = typeParameters.stream().map(
                p -> (Type)new UserDefinedType(p.getOrigin(),
                        new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null))).toList();

        if (classNeeded) {
            var clazz = new ClassDecl(origin, new Name(name.getOrigin(), nameCompiler.CLASS_PREFIX + name.getValue()), null,
                    typeParameters, classMembers, List.of(new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs))), false);
            return List.of(trait, clazz);
        } else {
            return List.of(trait);
        }
    }

    /**
     * To support 'super(...)' calls, we translate each Java constructor to an 'init' method in the Dafny trait
     * The Dafny class constructor then calls the init method of the related trait, and of the trait of its parent type.
     */
    private Method constructorToInitMethod(String className, Constructor constructor) {
        if (constructor.getBody() == null) {
            return null;
        }
        BlockStmt body = new BlockStmt(constructor.getBody().getOrigin(), null, List.of(),
                constructor.getBody().getBodyInit());
        Name nameNode = new Name(constructor.getNameNode().getOrigin(), getInitMethodName(className, constructor.getNameNode().getValue()));
        var frameExpressions = new ArrayList<>(constructor.getMod().getExpressions());
        var modClause = new Specification<>(frameExpressions, constructor.getMod().getAttributes());
        frameExpressions.add(new FrameExpression(constructor.getOrigin(), new ThisExpr(constructor.getOrigin()), null));
        return new Method(constructor.getOrigin(), nameNode, constructor.getAttributes(),
                constructor.getIsGhost(), constructor.getSignatureEllipsis(), constructor.getTypeArgs(), constructor.getIns(),
                constructor.getReq(), constructor.getEns(), constructor.getReads(), constructor.getDecreases(),
                modClause,
                false, List.of(), body, false);
    }

    private Symbol.ClassSymbol getCurrentTypeSymbol(JCTree.JCClassDecl classDecl) {
        return typeForWhichCurrentClassIsDefiningContract == null ? classDecl.sym : typeForWhichCurrentClassIsDefiningContract;
    }

    private boolean typeHasAContract(com.sun.tools.javac.code.Type type) {
        return this.externalContracts.containsKey(type.tsym) || typeHasSource(type.tsym);
    }

    private boolean typeHasSource(Symbol.TypeSymbol typeSymbol) {
        var trees = JavacTrees.instance(context);
        return trees.getTree(typeSymbol) != null;
    }

    private List<TypeParameter> translateTypeParameters(List<JCTree.JCTypeParameter> typarams) {
        return typarams.stream().map(p -> {
            var name = getName(p, p.getName());
            var bounds = p.bounds.map(this::translateType);
            // All Java type parameters are implicitly "extends Object",
            // and this needs to be explicit in the Dafny translation
            // so we can invoke equals() on any Java values,
            // even those translated to value types.
            var tpOrigin = toOrigin(p);
            var symtab = Symtab.instance(context);
            var objectName = nameCompiler.getCompiledName(symtab.objectType.tsym);
            bounds = bounds.prepend(new UserDefinedType(tpOrigin, new NameSegment(tpOrigin, objectName, null)));
            return new TypeParameter(toOrigin(p),
                    name, null, TPVarianceSyntax.NonVariant_Strict,
                    new TypeParameterCharacteristics(
                            TypeParameterEqualitySupportValue.Unspecified,
                            TypeAutoInitInfo.MaybeEmpty,
                            false
                    ),
                    bounds);
        }).toList();
    }

    static final String builtinFile = "/builtin-contracts.java";
    private boolean isAlreadyVerified() {
        return compilationUnit.getSourceFile().getName().equals(builtinFile);
    }

    private IndDatatypeDecl translateEnum(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        List<DatatypeCtor> constructors = new ArrayList<>();
        for(var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var variableName = nameCompiler.getCompiledName(variableDecl.sym);
                Name constructorName = getName(variableDecl, variableName);
                constructors.add(new DatatypeCtor(declToOrigin(variableDecl, constructorName), constructorName, 
                        null, false, List.of()));

            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }

    private static boolean isEnum(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            return classType.supertype_field != null && ((Symbol.ClassSymbol) classType.supertype_field.tsym).fullname.contentEquals("java.lang.Enum");
        }
        return false;
    }

    boolean isRecord(com.sun.tools.javac.code.Type type) {
        return type instanceof com.sun.tools.javac.code.Type.ClassType classType
                && (classType.asElement().flags() & Flags.RECORD) != 0;
    }

    private IndDatatypeDecl translateRecord(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        assert classDecl.getKind() == Tree.Kind.RECORD;
        if (isAnnotatedRecursive(classDecl.type, Modifiable.class)) {
            reportError(origin, "modifiableForbidden", "a record class");
        }

        var typeParams = translateTypeParameters(classDecl.typarams);

        var traits = getCurrentTypeSymbol(classDecl)
                .getInterfaces().stream()
                .filter(this::typeHasAContract)
                .map(baseType -> translateType(null, baseType, origin))
                .toList();

        var comps = TreeInfo.recordFields(classDecl);
        var ctorParams = comps.stream()
                .map(this::translateField)
                .filter(Objects::nonNull)
                .map(field -> new Formal(
                        field.getOrigin(), field.getNameNode(),
                        field.getExplicitType(),
                        false, true,
                        null, null,
                        false, false, false,
                        null
                ))
                .toList();
        var ctors = List.of(new DatatypeCtor(
                origin,
                name,
                null,
                false,
                ctorParams
        ));

        var compNames = comps.stream()
                .map(JCTree.JCVariableDecl::getName)
                .map(com.sun.tools.javac.util.Name::toString)
                .collect(Collectors.toSet());
        var members = new ArrayList<MemberDecl>();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl varDecl
                    && compNames.contains(varDecl.getName().toString()) ) {
                // Don't translate fields that arise from record components
                continue;
            } else if (member instanceof JCTree.JCMethodDecl methodDecl) {
                // No constructors should be translated:
                // explicit constructors are not allowed/supported,
                // and the implicit canonical constructor is unneeded to construct datatype values.
                if (TreeInfo.isConstructor(methodDecl)) {
                    if (!isSyntheticCanonicalConstructor(methodDecl)) {
                        reportError(member, "notSupported", "explicit record constructor");
                    }
                    continue;
                }
                var methodName = methodDecl.getName().toString();
                var params = methodDecl.getParameters();
                if (compNames.contains(methodName) && params.isEmpty()) {
                    reportError(member, "notSupported", "explicit record component accessor method");
                    continue;
                } else if ("equals".equals(methodName)
                        && params.length() == 1
                        && params.getFirst().type.toString().equals(Object.class.getName())) {
                    reportError(member, "notSupported", "overridden equals method in record");
                    continue;
                } else if ("hashCode".equals(methodName) && params.isEmpty()) {
                    reportError(member, "notSupported", "overridden hashCode method in record");
                    continue;
                }
            }
            var dafnyMember = translateMember(member);
            if (dafnyMember != null) {
                members.add(dafnyMember);
            }
        }

        return new IndDatatypeDecl(origin, name, null, typeParams, members, traits, ctors, false);
    }

    /**
     * Returns whether the declaration is a record's synthetic (implicit) canonical constructor.
     */
    private static boolean isSyntheticCanonicalConstructor(JCTree.JCMethodDecl methodDecl) {
        // Ideally we'd check for the SYNTHETIC flag, but it's not set.
        // So instead we check for its body: just a lone "super()" call.
        var body = methodDecl.getBody().getStatements();
        return TreeInfo.isCanonicalConstructor(methodDecl)
                && body.length() == 1
                && body.getFirst() instanceof JCTree.JCExpressionStatement stmt
                && TreeInfo.isSuperCall(stmt)
                && TreeInfo.args(stmt.getExpression()).isEmpty();
    }

    static class NotImplementedException extends RuntimeException {
        public NotImplementedException(String message) {
            super(message);
        }
    }

    MemberDecl translateMember(JCTree member) {
        switch (member) {
            case JCTree.JCClassDecl classDecl -> {
                return null;
            }
            case JCTree.JCMethodDecl method -> {
                return translateMethodDecl(method);
            }
            case JCTree.JCVariableDecl variableDecl -> {
                return translateField(variableDecl);
            }
            default -> throw new NotImplementedException(member.getClass().getName());
        }
    }

    private @Nullable Field translateField(JCTree.JCVariableDecl variableDecl) {
        var varFlags = variableDecl.getModifiers().getFlags();
        Name fieldName = getName(variableDecl, variableDecl.sym);
        IOrigin origin = declToOrigin(variableDecl, fieldName);
        Type type = translateType(variableDecl.getModifiers(), variableDecl.vartype.type, toOrigin(variableDecl.vartype));
        if (variableDecl.getInitializer() != null) {
            if (varFlags.contains(Modifier.FINAL)) {
                var rhs = expressionCompiler.toExpr(variableDecl.getInitializer());
                var isStatic = varFlags.contains(Modifier.STATIC);
                return new ConstantField(origin, fieldName, getAttributes(origin), false, type, rhs, isStatic, false);
            }

            // Keep this variable declaration in the initializers list to be added to constructors laters
            initializers.add(variableDecl);
        }
        return new Field(origin, fieldName, null, false, type);
    }

    private Attributes getAttributes(IOrigin origin) {
        return isAlreadyVerified() ? getVerifyFalse(origin) : null;
    }

    private static Attributes getVerifyFalse(IOrigin origin) {
        return new Attributes(origin, "verify", List.of(new LiteralExpr(origin, false)), null);
    }

    private boolean isNullable(JCTree.JCModifiers modifiers) {
        return isAnnotated(modifiers, com.aws.jverify.Nullable.class);
    }

    private boolean isNullable(com.sun.tools.javac.code.Type type) {
        return isAnnotated(type, com.aws.jverify.Nullable.class);
    }

    /**
     * Returns {@code true} if the given modifier tree contains an annotation of the given class.
     */
    private boolean isAnnotated(JCTree.JCModifiers modifiers, Class<? extends Annotation> clazz) {
        return modifiers != null && modifiers.getAnnotations().stream().anyMatch(a ->
                TreeInfo.symbol(a.getAnnotationType()) instanceof Symbol symbol
                        && symbol.flatName().contentEquals(clazz.getName()));
    }

    /**
     * Returns {@code true} if the given type is annotated with the given annotation class.
     */
    private boolean isAnnotated(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        var metadata = type.getMetadata(TypeMetadata.Annotations.class);
        // In some JDK distributions, this conditional is necessary to detect the annotation.
        if (metadata != null && metadata.annotationBuffer().stream()
                .anyMatch(s -> s.type.tsym.getQualifiedName().contentEquals(clazz.getName()))) {
            return true;
        }
        return type.getAnnotation(clazz) != null || type.tsym.getAnnotation(clazz) != null;
    }

    /**
     * Returns {@code true} if the given type or any of its supertypes is annotated with the given annotation class.
     */
    private boolean isAnnotatedRecursive(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        var types = Types.instance(context);
        return types.closure(type).stream().anyMatch(t -> isAnnotated(t, clazz));
    }

    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        return translateMethodOrLambda(method, method.getModifiers(), method.sym, method.body, method.typarams, null);
    }

    /**
     * @param sourceBody Either a JCBlock or a JCExpression. The latter is for the benefit of lambda translation.
     */
    @Nullable
    public MethodOrFunction translateMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                    Symbol.MethodSymbol methodSymbol,
                                                    JCTree sourceBody,
                                                    List<JCTree.JCTypeParameter> typeParameters,
                                                    @Nullable MethodOrLoopContract contract
    ) {
        if (typeForWhichCurrentClassIsDefiningContract != null && isSynthetic(source, methodSymbol)) {
            return null;
        }
        this.symbolsWithAContract.add(methodSymbol);

        var annotations = modifiers.getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));

        boolean shouldVerify = processVerifyAnnotationAndPop(annotationsByName);

        if (annotationsByName.containsKey(InheritContract.class.getName())) {
// Hints for whenever this is implemented.
//            var types = Types.instance(context);
//            var container = method.sym.enclClass();
//            var impl = method.sym.implemented(container, types);
            reportError(source, "notSupported", "@InheritContract");
            return null;
        }

        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        boolean isPure;
        if (externalContract == null) {
            isPure = annotationsByName.containsKey(Pure.class.getName());
        } else {
            isPure = externalContract.isPure;
        }
        if (isPure) {
            return translatePureMethodOrLambda(source, modifiers, methodSymbol, sourceBody, typeParameters, shouldVerify, contract);
        } else {
            return translateImpureMethodOrLambda(source, modifiers, methodSymbol, sourceBody, typeParameters, shouldVerify, contract);
        }
    }

    private MethodOrConstructor translateImpureMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                              Symbol.MethodSymbol methodSymbol, JCTree sourceBody,
                                                              List<JCTree.JCTypeParameter> typeParameters,
                                                              boolean shouldVerify,
                                                              @Nullable MethodOrLoopContract contractOverride) {
        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var bodyOrigin = toOrigin(sourceBody);

        var dafnyTypeParameters = translateTypeParameters(typeParameters);

        var methodCompiler = new BlockCompiler(this);
        var name = getName(source, methodSymbol);
        var origin = declToOrigin(source, name);
        var isStatic = isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol);

        MethodOrLoopContract header;
        List<Statement> bodyStatements = null;
        if (sourceBody instanceof JCTree.JCExpression expressionBody) {
            if (shouldVerify) {
                bodyStatements = List.of(
                        new ReturnStmt(bodyOrigin, null, List.of(
                                new ExprRhs(bodyOrigin, null, expressionCompiler.toExpr(expressionBody)))));
            }
            header = externalContract;
            if (header == null) {
                header = new MethodOrLoopContract(source, false);
            }
            if (contractOverride != null) {
                header = contractOverride;
            }
        } else {
            if (sourceBody == null) {
                header = externalContract;
                if (contractOverride != null) {
                    header = contractOverride;
                }
                if (header == null) {
                    return null;
                }
            } else {
                if (!(source instanceof JCTree.JCFieldAccess fa && fa.sym instanceof Symbol.DynamicMethodSymbol) && externalContract != null) {
                    reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                if (contractOverride != null) {
                    header = contractOverride;
                } else {
                    header = new MethodOrLoopContract(source, false);
                }
                List<JCTree.JCStatement> postHeader = methodCompiler.translateHeader(((JCTree.JCBlock) sourceBody).stats, header, true);
                if (shouldVerify) {
                    bodyStatements = methodCompiler.translateStatements(postHeader);
                }
            }
        }
        applyInvariants(sourceBody, modifiers, methodSymbol, header);
        methodCompiler.checkEmptyExpressions(source, header.invariants, "invariants", "method");

        var outs = new ArrayList<Formal>();
        if (methodSymbol.type.getReturnType() != null) {
            var returnType = translateType(methodSymbol.type.getReturnType(), bodyOrigin);
            if (returnType != null) {
                outs.add(makeReturnFormal(origin, returnType));
            }
        }

        if (isConstructor(methodSymbol)) {
            DividedBlockStmt body;
            if (shouldVerify) {
                var treeMaker = TreeMaker.instance(context);

                var newBodyStatements = new ArrayList<Statement>();
                for (JCTree.JCVariableDecl variableDecl : initializers) {
                    var rhs = variableDecl.getInitializer();
                    var assignStmt = treeMaker.Assignment(variableDecl.sym,rhs);
                    newBodyStatements.addAll(methodCompiler.translateStatement(assignStmt, bodyOrigin));
                }
                newBodyStatements.addAll(bodyStatements);
                bodyStatements = newBodyStatements;

                body = new DividedBlockStmt(bodyOrigin, null, List.of(), bodyStatements, null, List.of());
            } else {
                body = null;
            }

            return new Constructor(origin, name, null, false, null, dafnyTypeParameters, ins,
                    header.preconditions, header.postconditions, header.getReads(),
                    header.getDecreases(), header.getModifies(),
                    body);
        } else {
            BlockStmt body;
            if (bodyStatements != null) {
                body = new BlockStmt(bodyOrigin, null, List.of(), bodyStatements);
            } else {
                body = null;
            }
            return new Method(origin, name, null, false, null, dafnyTypeParameters,
                    ins, header.preconditions, header.postconditions, header.getReads(),
                    header.getDecreases(), header.getModifies(),
                    isStatic, outs,
                    body, false);
        }
    }

    public boolean isSynthetic(JCTree methodNode, Symbol.MethodSymbol methodSymbol) {
        var containerPos = JavacTrees.instance(context).getTree(methodSymbol.enclClass()).pos;
        return methodNode.pos == containerPos;
    }

    private @Nullable MethodOrLoopContract findExternalContract(Symbol.MethodSymbol methodSymbol) {
        var enclosingClass = methodSymbol.enclClass();
        var contractor = this.externalContracts.get(enclosingClass);
        if (contractor != null) {
            return contractor.methodContracts().get(methodSymbol);
        }
        return null;
    }

    private Function translatePureMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers, 
                                                 Symbol.MethodSymbol methodSymbol, JCTree sourceBody, 
                                                 List<JCTree.JCTypeParameter> typeParameters, boolean shouldVerify,
                                                 @Nullable MethodOrLoopContract contractOverride) {
        var bodyOrigin = toOrigin(sourceBody);

        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var methodCompiler = new BlockCompiler(this);
        var name = getName(source, methodSymbol);
        var origin = declToOrigin(source, name);
        var isStatic = isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol);
        Expression body = null;
        MethodOrLoopContract header;
        var returnType = translateType(methodSymbol.type.getReturnType(), bodyOrigin);
        if (returnType == null) {
            reportError(source, "pureMethodsNeedsReturnType");
            return null;
        }
        if (sourceBody instanceof JCTree.JCExpression) {
            if (shouldVerify) {
                body = expressionCompiler.toExpr((JCTree.JCExpression) sourceBody);
            }

            header = externalContract;
            if (contractOverride != null) {
                header = contractOverride;
            }
            if (header == null) {
                header = new MethodOrLoopContract(source, true);
            }
        } else {
            if (sourceBody == null) {
                header = externalContract;
                if (contractOverride != null) {
                    header = contractOverride;
                }
                if (header == null) {
                    header = new MethodOrLoopContract(source, false);
                    reportError(source, "bodylessMethodWithoutContract", methodSymbol.name.toString());
                }
            } else {
                if (!(source instanceof JCTree.JCFieldAccess fa && fa.sym instanceof Symbol.DynamicMethodSymbol) && externalContract != null) {
                    reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                if (contractOverride != null) {
                    header = contractOverride;
                } else {
                    header = new MethodOrLoopContract(source, true);
                }
                var postHeader = methodCompiler.translateHeader((JCTree.JCBlock) sourceBody, header, true);
                if (postHeader.size() != 1) {
                    reportError(source, "pureMethodMultipleStatements");
                    return null;
                }

                var statement = postHeader.getFirst();
                if (shouldVerify) {
                    if (statement instanceof JCTree.JCReturn returnStatement) {
                        body = expressionCompiler.toExpr(returnStatement.expr);
                    } else {
                        reportError(source, "pureMethodNeedsReturnStatement");
                        return null;
                    }
                }
            }
        }
        applyInvariants(sourceBody, modifiers, methodSymbol, header);

        var dafnyTypeParameters = translateTypeParameters(typeParameters);
        return new Function(origin, name, null, false, null, dafnyTypeParameters,
                ins, header.preconditions, header.postconditions, header.getReads(),
                header.getDecreases(), isStatic, false, makeReturnFormal(origin, returnType),
                returnType, body, null, null);
    }

    private boolean processVerifyAnnotationAndPop(Map<String, JCTree.JCAnnotation> annotationsByName) {
        processVerifyAnnotation(annotationsByName);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        return shouldVerify;
    }

    private List<Formal> getIns(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getParameters().map(jvd -> {
            var trees = JavacTrees.instance(this.context);
            var parameter = trees.getTree(jvd);
            var parameterOrigin  = this.toOrigin(parameter);
            Name formalName = new Name(parameterOrigin, jvd.name.toString());
            var syntacticType = translateType(jvd.type, parameterOrigin);
            return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        });
    }

    private Formal makeReturnFormal(IOrigin origin, Type syntacticType) {
        var name = new Name(origin, nameCompiler.METHOD_RETURN_VARIABLE_NAME);
        return new Formal(origin, name, syntacticType, false, false, null, null, false, false, false, null);
    }

    private static boolean isStatic(JCTree.JCModifiers modifiers) {
        return (modifiers.flags & Flags.STATIC) == Flags.STATIC;
    }

    private JCDiagnostic.DiagnosticPosition positionFromNode(JCTree node, JCTree.JCCompilationUnit compilationUnit) {
        return new JCDiagnostic.DiagnosticPosition() {
            @Override
            public JCTree getTree() {
                return node;
            }

            @Override
            public int getStartPosition() {
                return node.getStartPosition();
            }

            @Override
            public int getPreferredPosition() {
                return node.getPreferredPosition();
            }

            @Override
            public int getEndPosition(EndPosTable endPosTable) {
                return node.getEndPosition(compilationUnit.endPositions);
            }
        };
    }

    private void applyInvariants(JCTree source, JCTree.JCModifiers modifiers, Symbol.MethodSymbol methodSymbol, MethodOrLoopContract header) {
        boolean isPublic = (modifiers.flags & Flags.PUBLIC) != 0;
        boolean isStaticMethod = isStatic(modifiers);
        
        // Only apply invariants to public instance methods (not static methods)
        if (isPublic && !isStaticMethod) {
            for(var invariant : invariants) {
                var memberName = nameCompiler.getCompiledName(invariant);
                var invariantName = getName(source, memberName);
                var invariantOrigin = declToOrigin(source, invariantName);
                ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                        memberName, null), null, new ActualBindings(List.of()), null);
                var invariantCall = new AttributedExpression(call,null, null);
                if (!isConstructor(methodSymbol)) {
                    header.preconditions.add(invariantCall);
                }
                header.postconditions.add(invariantCall);
            }
        }
    }

    static LiteralExpr getHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, true);
    }

    public boolean isEnum(JCTree.JCExpression selected) {
        if (selected instanceof JCTree.JCIdent jcIdent) {
            if (jcIdent.sym instanceof Symbol.ClassSymbol classSymbol) {
                return isEnum(classSymbol.type);
            }
        }
        return false;
    }

    public @Nullable Type translateType(JCTree tree) {
        return translateType(null, tree);
    }

    public @Nullable Type translateType(JCTree.JCModifiers modifiers, JCTree tree) {
        return translateType(modifiers, tree.type, toOrigin(tree));
    }

    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin) {
        return translateType(null, type, origin);
    }

    @Nullable
    public Type translateType(JCTree.JCModifiers modifiers, com.sun.tools.javac.code.Type type, IOrigin origin) {
        // In several cases annotations that come right before types
        // end up bound to tree nodes such as variable declarations instead of the type.
        // Hence, for something like `@Nullable int[] foo;`, which should be interpreted as `(@Nullable int)[] foo;`,
        // we apply the modifier to the innermost element type of an array type.
        var isNullable = isNullable(type) || (isNullable(modifiers) && !(type instanceof com.sun.tools.javac.code.Type.ArrayType));
        var nullableSuffix = isNullable ? "?" : "";

        var primitiveTypeKind = toPrimitiveTypeModuloBoxing(type);
        if (primitiveTypeKind != null) {
            if (isNullable) {
                reportError(origin, "notSupported", "nullable primitive type");
            }
            switch (primitiveTypeKind) {
                case VOID -> {
                    return null;
                }
                case BOOLEAN -> {
                    return new BoolType(origin);
                }
                case INT, SHORT, LONG -> {
                    var mirrors = type.getAnnotationMirrors();
                    var natAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Nat.class.getName())).findFirst();
                    var isNat = natAnnotation.isPresent();
                    var boundedAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Unbounded.class.getName())).findFirst();
                    var isBounded = boundedAnnotation.isEmpty();
                    if (isBounded) {
                        var number = switch (primitiveTypeKind) {
                            case SHORT -> 16;
                            case INT -> 32;
                            case LONG -> 64;
                            default -> throw new IllegalStateException("Unexpected value: " + primitiveTypeKind);
                        };
                        if (isNat) {
                            number = number - 1;
                            return new UserDefinedType(origin, new NameSegment(origin, "nat" + number, null));
                        } else {
                            return new UserDefinedType(origin, new NameSegment(origin, "int" + number, null));
                        }
                    } else {
                        if (primitiveTypeKind != TypeKind.INT) {
                            reportError(origin, "unboundedNonInt", type.toString());
                        }
                        if (isNat) {
                            return new UserDefinedType(origin, new NameSegment(origin, "nat", null));
                        } else {
                            return new IntType(origin);
                        }
                    }
                }
                case BYTE -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "byte", null));
                }
                case CHAR -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "char16", null));
                }
                case FLOAT -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "float", null));
                }
                case DOUBLE -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "double", null));
                }
            }

            reportError(origin, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
            return null;
        }

        switch (type) {
            case com.sun.tools.javac.code.Type.ArrayType arrayTypeTree -> {
                // TODO: Assuming nullable here means it's not possible to have non-nullable array elements?
                var elemType = translateType(modifiers, arrayTypeTree.elemtype, origin);
                if (elemType == null) {
                    // should be unreachable
                    throw new IllegalArgumentException("Array type without element type");
                }
                return new UserDefinedType(origin, new NameSegment(origin, "array" + nullableSuffix, List.of(elemType)));
            }
            case com.sun.tools.javac.code.Type.ClassType classType -> {
                var className = classType.asElement().flatName();
                if (className.toString().equals(String.class.getName())) {
                    if (!isNullable) {
                        return new UserDefinedType(origin, new NameSegment(origin, "jstring", null));
                    }
                    reportError(origin, "notSupported", "nullable String type");
                    return null;
                }

                if (isRecord(classType) && isNullable) {
                    reportError(origin, "notSupported", "nullable record type");
                    return null;
                }
                // TODO: Better check. But watch out for getName() and $
                if (className.toString().equals("com.aws.jverify.JVerify$Sequence")) {
                    var arguments = classType.getTypeArguments().stream().map(a -> translateType(null, a, origin)).toList();
                    return new SeqType(origin, arguments);
                }

                // Remove the name qualification because we do not support that yet
                var compiledName = nameCompiler.getCompiledName(classType.tsym);
                var arguments = classType.getTypeArguments().stream().map(a -> translateType(null, a, origin)).toList();
                if (arguments.isEmpty()) {
                    arguments = null;
                }
                NameSegment nameSegment = new NameSegment(origin, compiledName, arguments);
                if (isNullable) {
                    nameSegment = new NameSegment(nameSegment.getOrigin(), nameSegment.getName() + nullableSuffix, nameSegment.getOptTypeArguments());
                }
                return new UserDefinedType(origin, nameSegment);
            }
            case com.sun.tools.javac.code.Type.TypeVar typeVar -> {
                return new UserDefinedType(origin, new NameSegment(origin, nameCompiler.getCompiledName(typeVar.tsym), null));
            }
            default -> {
            }
        }
        reportError(origin, "notSupported", "type " + type.getClass().getName());
        return null;
    }

    /**
     * If the specified tree represents either a primitive type or a boxed primitive type,
     * returns the corresponding {@link TypeKind},
     * otherwise returns {@code null}.
     */
    private @Nullable TypeKind toPrimitiveTypeModuloBoxing(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.JCVoidType) {
            return TypeKind.VOID;
        } else if (type instanceof com.sun.tools.javac.code.Type.JCPrimitiveType primitiveType) {
            return primitiveType.getKind();
//        } else if (type instanceof com.sun.tools.javac.code.Type.ClassType classType
//                && classType.tsym.packge().getQualifiedName().contentEquals("java.lang")) {
//            var name = classType.tsym.getSimpleName().toString();
//            if (name.equals(Boolean.class.getSimpleName())) return TypeKind.BOOLEAN;
//            if (name.equals(Byte.class.getSimpleName())) return TypeKind.BYTE;
//            if (name.equals(Short.class.getSimpleName())) return TypeKind.SHORT;
//            if (name.equals(Integer.class.getSimpleName())) return TypeKind.INT;
//            if (name.equals(Long.class.getSimpleName())) return TypeKind.LONG;
//            if (name.equals(Character.class.getSimpleName())) return TypeKind.CHAR;
//            if (name.equals(Float.class.getSimpleName())) return TypeKind.FLOAT;
//            if (name.equals(Double.class.getSimpleName())) return TypeKind.DOUBLE;
        }

        return null;
    }

    Name getName(JCTree tree, com.sun.tools.javac.util.Name name) {
        return getName(tree, name.toString());
    }
    
    Name getName(JCTree tree, Symbol symbol) {
        return getName(tree, nameCompiler.getCompiledName(symbol), symbol.name.length());
    }

    Name getName(JCTree tree, String name) {
        return getName(tree, name, name.length());
    }

    Name getName(JCTree tree, String name, int length) {
        var positionCalculator = new PositionCalculator(compilationUnit);
        int startPos = positionCalculator.getStartPos(tree);
        var startToken = positionCalculator.toToken(startPos);
        var endToken = positionCalculator.toToken(startPos + length);
        var origin = startToken == null ? contextOrigins.peek() : new TokenRangeOrigin(startToken, endToken);
        return new Name(origin, name);
    }

    public static boolean isConstructor(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.name == methodSymbol.name.table.names.init;
    }

    private SourceOrigin declToOrigin(JCTree node, Name name) {
        var entireRange = toOrigin(node);
        return new SourceOrigin(originToRange(entireRange), originToRange(name.getOrigin()));
    }
    
    public IOrigin toOrigin(JCTree node) {
        var positionCalculator = new PositionCalculator(compilationUnit);
        var startToken = positionCalculator.toToken(TreeInfo.getStartPos(node));
        if (startToken == null) {
            return contextOrigins.peek();
        }
        int endPos = positionCalculator.getEndPos(node);
        var endToken = endPos == Position.NOPOS 
                ? positionCalculator.toToken(TreeInfo.getStartPos(node) + 1) 
                : positionCalculator.toToken(endPos);
        return new TokenRangeOrigin(startToken, endToken);
    }

    private TokenRange originToRange(IOrigin tokenRangeOrigin) {
        if (tokenRangeOrigin instanceof SourceOrigin sourceOrigin) {
            return new TokenRange(sourceOrigin.getEntireRange().getStartToken(), sourceOrigin.getEntireRange().getEndToken());
        } else if (tokenRangeOrigin instanceof TokenRangeOrigin trOrigin) {
            return new TokenRange(trOrigin.getStartToken(), trOrigin.getEndToken());
        } else {
            throw new NotImplementedException(tokenRangeOrigin.getClass().getName());
        }
    }

    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return !(methodSymbol instanceof Symbol.DynamicMethodSymbol)
                && methodSymbol.outermostClass().className().contentEquals(JVERIFY_CLASS);
    }

    /**
     * If the specified invocation's method is from the JVerify library,
     * returns its {@link Symbol.MethodSymbol}.
     * Otherwise, returns {@code null}.
     */
    public static Symbol.MethodSymbol getJVerifyMethod(JCTree.JCMethodInvocation invocation) {
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        return fromJVerify(methodSymbol) ? methodSymbol : null;
    }
}

/**
 * Occurs when the contracts that we expect from Java resolution are violated
 * Indicates a JVerify compiler bug
 */
class JavaViolationException extends RuntimeException {
    public JavaViolationException() {
        super();
    }
    
    public JavaViolationException(String message) {
        super(message);
    }
}


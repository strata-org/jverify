package com.aws.jverify.verifier;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;

import com.sun.tools.javac.tree.TreeInfo;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/*
TODO: issue with class with ExternalContract, as their name is changed at the last
minute during translateClass (code is from the of the external contract class)

Need to check how to handle this. Could be a limitation still (no overloading in
external contract class)

 */

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public final Context context;
    JCTree.JCCompilationUnit compilationUnit;
    Stack<IOrigin> contextOrigins = new Stack<>();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    
    private JCDiagnostic.Factory diagnosticFactory;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;
    private ConstructorDisambiguator constructorDisambiguator;


    public JavaToDafnyCompiler(Context context) {
        this.context = context;
        this.constructorDisambiguator = new ConstructorDisambiguator(this);
        shouldVerifies.push(ShouldVerifyMode.DefaultYes);
    }
    
    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, List<JavaFileObject> files) {        
        JavacTool compiler = JavacTool.create();
        
        if (!Files.exists(options.libraryJar().toAbsolutePath())) {
            throw new IllegalArgumentException("Could not find file: " + options.libraryJar());
        }

        // don't assume the argument is modifiable
        files = new ArrayList<>(files);
        files.add(new SourceFile("builtin-contracts.java", Common.getResourceFile(getClass(), builtinFile)));

        var classpathEntries = new ArrayList<Path>();
        classpathEntries.add(options.libraryJar().toAbsolutePath());
        classpathEntries.addAll(options.extraClassPathEntries());
        var classpath = classpathEntries.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        var javacOptions = List.of("-classpath", classpath);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                null,
                null,
                diagnostics,
                javacOptions,
                null,
                files,
                context
        );
                
        List<FileStart> filesStarts = new ArrayList<>();
        var parsed = task.parse();
        task.analyze();
        this.diagnosticFactory = JCDiagnostic.Factory.instance(context);

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                JCDiagnostic.DiagnosticPosition position = new DiagnosticPositionFromDiagnostic(diagnostic);

                this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                        new DiagnosticSource(diagnostic.getSource(), null), position, "javaError",
                        diagnostic.getMessage(Locale.ENGLISH)));
                
                return new FilesContainer(filesStarts);
            }
        }
        // Here handle all constructors for all classes of all files. Otherwise fail with multiple
        // classes
        for (var compilationUnit : parsed) {
            findExternalContracts((JCTree.JCCompilationUnit) compilationUnit);
        }
        for (var compilationUnit : parsed) {
            this.constructorDisambiguator.handleCompilationUnit((JCTree.JCCompilationUnit) compilationUnit);
        }
        for (var compilationUnit : parsed) {
            var fileStart = translateFile((JCTree.JCCompilationUnit) compilationUnit);
            filesStarts.add(fileStart);
        }

        return new FilesContainer(filesStarts);
    }
    
    public final Set<Symbol.ClassSymbol> classWithExternalContract = new HashSet<>();
    private void findExternalContracts(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        for (var typeDecl : compilationUnit.getTypeDecls()) {
            if (typeDecl instanceof JCTree.JCClassDecl classDecl) {
                var annotations = classDecl.getModifiers().getAnnotations();
                var annotationsByName = annotations.stream().collect(Collectors.toMap(
                        (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                        a -> a));
                var contractAnnotation = annotationsByName.get(Contract.class.getName());
                if (contractAnnotation != null) {
                    var arguments = getArguments(contractAnnotation);
                    Symbol.ClassSymbol classSymbol = getClassSymbol(arguments.get("value"));
                    if (!classWithExternalContract.add(classSymbol)) {
                        reportError(contractAnnotation, "duplicateContract", classDecl.name);
                    }
                }
            }
        }
    }

    private static Symbol.ClassSymbol getClassSymbol(JCTree.JCExpression valueArgument) {
        Symbol.ClassSymbol classSymbol;
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess &&
            fieldAccess.selected instanceof JCTree.JCIdent ident &&
            ident.sym instanceof Symbol.ClassSymbol classSymbol2)
        {
            classSymbol = classSymbol2;
        } else {
            throw new JavaViolationException();
        }
        return classSymbol;
    }



    private FileStart translateFile(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;

        ArrayList<TopLevelDecl> topLevelDecls = new ArrayList<>();
        Stack<Tree> remainingTypes = new Stack<>();
        remainingTypes.addAll(compilationUnit.getTypeDecls());
        while(!remainingTypes.isEmpty()) {
            var typeDecl = remainingTypes.pop();
            TopLevelDecl dafnyDecl = translateTypeDeclaration(typeDecl, remainingTypes);
            if (dafnyDecl != null) {
                topLevelDecls.add(dafnyDecl);
            }
        }
        return new FileStart(this.compilationUnit.sourcefile.toUri().toString(), topLevelDecls);
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

    List<AttributedExpression> invariants = new ArrayList<>();
    List<JCTree.JCVariableDecl> initializers = new ArrayList<>();
    
    enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }
    private final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();
    private boolean shouldVerify() {
        if (typeForWhichCurrentClassIsDefiningContract != null) {
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
    
    @Nullable TopLevelDecl translateTypeDeclaration(Tree tree, Stack<Tree> nestedTypes) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            System.out.println("translateTypeDeclaration: " + classDecl.name);
            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));

            processVerifyAnnotation(annotationsByName);

            Name name = getName(classDecl, classDecl.name);
            if (classWithExternalContract.contains(classDecl.sym)) {
                System.out.println("External class found");
                boolean isInterface = isInterface(classDecl);
                if (!isInterface && shouldVerify()) {
                    reportError(name.getOrigin(), "verifiedTypeWithExternalContract", classDecl.name);
                }
                return null;
            }
            var origin = declToOrigin(classDecl, name);
            contextOrigins.push(origin);

            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                if (isNestedClass(classDecl)) {
                    reportError(contractAnnotation, "nestedContractClass", classDecl.name);
                }
                var arguments = getArguments(contractAnnotation);
                typeForWhichCurrentClassIsDefiningContract = getClassSymbol(arguments.get("value"));
                name = new Name(name.getOrigin(), typeForWhichCurrentClassIsDefiningContract.name.toString());
            }
            if (annotationsByName.containsKey(Immutable.class.getName())) {
                reportError(classDecl, "notSupported", "@ValueType");
            }
            
            TopLevelDecl result;
            if (isEnum(classDecl.type)) {
                result = translateEnum(classDecl, origin, name);
            } 
            else {
                result = translateClass(nestedTypes, classDecl, origin, name);
            }
            typeForWhichCurrentClassIsDefiningContract = null;
            contextOrigins.pop();
            shouldVerifies.pop();
            return result;
        }
        if (tree instanceof JCTree jcTree) {
            reportError(jcTree, "notSupported", tree.getClass().getSimpleName());
            return null;
        } else {
            throw new NotImplementedException(tree.getClass().getName());
        }
    }

    private static boolean isInterface(JCTree.JCClassDecl classDecl) {
        return (classDecl.mods.flags & Flags.INTERFACE) != 0;
    }

    private static boolean isInterface(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.INTERFACE) != 0;
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

    static Object getLiteralValue(JCTree.JCExpression expression) {
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
    
    private ClassLikeDecl translateClass(Stack<Tree> nestedTypes, JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        invariants.clear();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (methodDecl.getModifiers().getAnnotations().stream().
                        anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident && 
                                ident.name.contentEquals("Invariant"))) {
                    var invariantName = getName(methodDecl, methodDecl.sym.name);
                    var invariantOrigin = declToOrigin(methodDecl, invariantName);
                    ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                            methodDecl.sym.name.toString(), null), null, new ActualBindings(List.of()), null);
                    invariants.add(new AttributedExpression(call,null, null)); 
                }
            }
        }

        var isInterface = typeForWhichCurrentClassIsDefiningContract == null ? isInterface(classDecl) :
                isInterface(typeForWhichCurrentClassIsDefiningContract);
        
        ArrayList<MemberDecl> members = new ArrayList<>();
        initializers.clear();
        // First translate all fields and store default initializers to add to constructors
        // Also rename constructors to allow for multiple constructors in each class
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
            if (!(member instanceof JCTree.JCVariableDecl variableDecl)) {
                var dafnyMember = translateMember(member, nestedTypes);
                if (dafnyMember != null) {
                    members.add(dafnyMember);
                }
            }
        }
        var definingSymbol = typeForWhichCurrentClassIsDefiningContract == null ? classDecl.sym : typeForWhichCurrentClassIsDefiningContract;
        var interfaces = definingSymbol.getInterfaces();
        var trees = JavacTrees.instance(context);
        var superTraits = interfaces.stream().
                filter(type -> this.classWithExternalContract.contains(type.tsym) || trees.getTree(type.tsym) != null).
                map((com.sun.tools.javac.code.Type type) ->
                    new UserDefinedType(origin, new NameSegment(origin, type.tsym.name.toString(), null))).
                collect(Collectors.<Type>toList());
        if (isInterface) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "object", null)));
            return new TraitDecl(origin, name, null, List.of(), members, superTraits, false);
        } else {
            return new ClassDecl(origin, name, null, List.of(), members, superTraits, false);
        }
    }
    
    static final String builtinFile = "/builtin-contracts.java";
    private boolean isAlreadyVerified() {
        return compilationUnit.getSourceFile().getName().equals(builtinFile);
    }

    private IndDatatypeDecl translateEnum(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        List<DatatypeCtor> constructors = new ArrayList<>();
        for(var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                Name constructorName = getName(variableDecl, variableDecl.name);
                constructors.add(new DatatypeCtor(declToOrigin(variableDecl, constructorName), constructorName, 
                        null, false, List.of()));

            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }

    private static boolean isEnum(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            return ((Symbol.ClassSymbol) classType.supertype_field.tsym).fullname.contentEquals("java.lang.Enum");
        }
        return false;
    }

    static class NotImplementedException extends RuntimeException {
        public NotImplementedException(String message) {
            super(message);
        }
    }

    MemberDecl translateMember(JCTree member, Stack<Tree> nestedTypes) {
        switch (member) {
            case JCTree.JCClassDecl classDecl -> {
                nestedTypes.add(classDecl);
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
        Name fieldName = getName(variableDecl, variableDecl.name);
        IOrigin origin = declToOrigin(variableDecl, fieldName);
        Type type = toType(variableDecl.vartype, isNullable(variableDecl.getModifiers()));
        if (variableDecl.getInitializer() != null) {
            var isFinal = (variableDecl.mods.flags & Flags.FINAL) != 0;
            if (isFinal) {
                var rhs = toExpr(variableDecl.getInitializer());
                var isStatic = (variableDecl.mods.flags & Flags.STATIC) != 0;
                return new ConstantField(origin, fieldName, getAttributes(origin), false, type, rhs, isStatic, false);
            } else {
                // Keep this variable declaration in the initalizers list to be added to constructors laters

                initializers.add(variableDecl);
            }
        }

        return new Field(origin, fieldName,
                null,
                false,
                type);
    }

    private Attributes getAttributes(IOrigin origin) {
        return isAlreadyVerified() ? getVerifyFalse(origin) : null;
    }

    private static Attributes getVerifyFalse(IOrigin origin) {
        return new Attributes(origin, "verify", List.of(new LiteralExpr(origin, false)), null);
    }

    private boolean isNullable(JCTree.JCModifiers modifiers) {
        return modifiers.getAnnotations().stream().anyMatch(
                a -> a.getAnnotationType() instanceof JCTree.JCIdent ident && ident.name.contentEquals("Nullable"));
    }

    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {

        var methodCompiler = new MethodCompiler(this);
        var name = getName(method, method.sym.name);
        var origin = declToOrigin(method, name);

        var annotations = method.getModifiers().getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));
        
        processVerifyAnnotation(annotationsByName);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        
        if (annotationsByName.containsKey(InheritContract.class.getName())) {
// Hints for whenever this is implemented.
//            var types = Types.instance(context);
//            var container = method.sym.enclClass();
//            var impl = method.sym.implemented(container, types);
            reportError(method, "notSupported", "@InheritContract");
            return null;
        }

        List<Formal> ins = method.getParameters().map(jvd ->
        {
            Name formalName = getName(jvd, jvd.getName());
            var syntacticType = toType(jvd.getType(), isNullable(jvd.getModifiers()));
            return new Formal(declToOrigin(jvd, formalName), formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        });
        var isStatic = (method.getModifiers().flags & Flags.STATIC) == Flags.STATIC;

        if (annotationsByName.containsKey(Pure.class.getName())) {
            var header = new HeaderContainer();
            var postHeader = methodCompiler.translateHeader(method.body.stats, header);
            applyInvariants(method, header);
            if (postHeader.size() != 1) {
                reportError(method, "pureMethodMultipleStatements");
                return null;
            }
            var returnType = toType(method.getReturnType());
            if (returnType == null) {
                reportError(method, "pureMethodsNeedsReturnType");
                return null;
            }

            var statement = postHeader.getFirst();
            Expression body;
            if (shouldVerify) {
                if (statement instanceof JCTree.JCReturn returnStatement) {
                    body = toExpr(returnStatement.expr);
                    return new Function(origin, name, null, false, null, List.of(),
                            ins, header.preconditions, header.postconditions, header.getReads(),
                            header.getDecreases(), isStatic, false, null, returnType,
                            body, null, null);
                } else {
                    reportError(method, "pureMethodNeedsReturnStatement");
                    return null;
                }
            } else {
                body = null;
            }
            return new Function(origin, name, null, false, null, List.of(),
                    ins, header.preconditions, header.postconditions, header.getReads(),
                    header.getDecreases(), isStatic, false, null, returnType,
                    body, null, null);
        } else {
            var header = new HeaderContainer();
            var postHeader = methodCompiler.translateHeader(method.getBody().stats, header);
            applyInvariants(method, header);
            methodCompiler.checkEmptyExpressions(method, header.invariants, "invariants", "method");

            if (header.returnNames.size() > 1) {
                reportError(method, "multipleReturnNames");
                return null;
            }
            var outs = new ArrayList<Formal>();
            if (method.getReturnType() != null) {
                var returnType = toType(method.getReturnType(), false);
                if (returnType != null) {
                    Name returnName;
                    if (header.returnNames.size() == 1) {
                        returnName = header.returnNames.getFirst();
                    } else {
                        returnName = new Name(origin, "r");
                    }
                    var f = new Formal(toOrigin(method.getReturnType()), returnName, returnType,
                            false, false, null, null, false, false, false, null);
                    outs.add(f);
                }
            }

            if (TreeInfo.isConstructor(method)) {

                var containerIsInterface = typeForWhichCurrentClassIsDefiningContract != null && 
                        isInterface(typeForWhichCurrentClassIsDefiningContract);
                if (containerIsInterface) {
                    var containerPos = JavacTrees.instance(context).getTree(method.sym.enclClass()).pos;
                    var synthetic = method.pos == containerPos;
                    if (synthetic) {
                        // ignore default constructors in interfaces classes
                        return null;
                    } else {
                        reportError(method, "constructorInInterfaceContract");
                        return null;
                    }
                }
                DividedBlockStmt body;
                if (shouldVerify) {
                    ArrayList<Statement> bodyStatements = new ArrayList<>();
                    var treeMaker = TreeMaker.instance(context);

                    for (JCTree.JCVariableDecl variableDecl : initializers) {
                      var rhs = variableDecl.getInitializer();
                      var assignStmt = treeMaker.Assignment(variableDecl.sym,rhs);
                      bodyStatements.addAll(methodCompiler.translateStatement(assignStmt));
                    }
                    bodyStatements.addAll(methodCompiler.translateStatements(postHeader));

                    body = new DividedBlockStmt(toOrigin(method.body), null, List.of(), bodyStatements, null, List.of());
                } else {
                    body = null;
                }
                Name ctorName = name;
                if (method.sym.name.contentEquals("<init>")) {
                    ctorName = new Name(origin, "_ctor");
                }


                    return new Constructor(origin, ctorName , null, false, null, List.of(), ins,
                        header.preconditions, header.postconditions, header.getReads(),
                        header.getDecreases(), header.getModifies(),
                        body);
            } else {
                BlockStmt body;
                if (shouldVerify) {
                    var bodyStatements = methodCompiler.translateStatements(postHeader);
                    body = new BlockStmt(toOrigin(method.body), null, List.of(), bodyStatements);
                } else {
                    body = null;
                }
                if (annotationsByName.containsKey(Proof.class.getName())) {
                    return new Method(origin, name, null, false, null, List.of(),
                            ins, header.preconditions, header.postconditions, header.getReads(),
                            header.getDecreases(), header.getModifies(), 
                            isStatic, outs,
                            body, false);
                } else {
                    return new Method(origin, name, null, false, null, List.of(),
                            ins, header.preconditions, header.postconditions, header.getReads(),
                            header.getDecreases(), header.getModifies(), isStatic, outs,
                            body, false);
                }
            }
        }
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

    private void applyInvariants(JCTree.JCMethodDecl method, HeaderContainer header) {
        boolean isPublic = (method.getModifiers().flags & Flags.PUBLIC) != 0;
        if (isPublic) {
            for(var invariant : invariants) {
                if (!TreeInfo.isConstructor(method)) {
                    header.preconditions.add(invariant);
                }
                header.postconditions.add(invariant);
            }
        }
    }

    public Expression toExpr(JCTree tree) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExpr(expression);
        }
        reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return getHole(toOrigin(tree));
    }

    private static LiteralExpr getHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, true);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr) {
        var origin = toOrigin(expr);
        if (expr instanceof JCTree.JCNewClass newClass) {
            var argBindings = newClass.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
            // Construct the ctor name BaseClass.m_ctor as a Type
            String ctorNameStr = newClass.constructor.name.toString();
            if (ctorNameStr.contentEquals("<init>")) {
                ctorNameStr = "_ctor";
            }
            Name ctorName = new Name(origin, ctorNameStr);
            var baseType = toExpr(newClass.clazz);
            var ty = new UserDefinedType(origin, new ExprDotName(origin, baseType, ctorName, null));

            return new AllocateClass(origin, null, ty,  new ActualBindings(argBindings));
        }
        if (expr instanceof JCTree.JCNewArray newArray) {
            var arrayDimensions = newArray.getDimensions().stream().map(d -> toExpr(d)).toList();
            var arrayInitializers = newArray.getInitializers();
            var arrayJavaType = newArray.getType();
            if (arrayJavaType instanceof JCTree.JCArrayTypeTree _) {
                reportError(expr, "notSupported", "multi-dimensional arrays");
            }
            var arrayDafnyType = toType(arrayJavaType, true);

            if (arrayInitializers != null && !arrayInitializers.isEmpty()) {
                reportError(expr, "notSupported", "new array with initializers");
            }
            return new AllocateArray(origin, null, arrayDafnyType, arrayDimensions, null);
        }
        var dafnyExpr = toExpr(expr);
        return new ExprRhs(origin, null, dafnyExpr);
    }
    
    private Expression toExpr(JCTree.JCExpression expr) {
        var origin = toOrigin(expr);
        if (expr instanceof JCTree.JCConditional conditional) {
            var condition = toExpr(conditional.getCondition());
            var thenBranch = toExpr(conditional.getTrueExpression());
            var elseBranch = toExpr(conditional.getFalseExpression());
            return new ITEExpr(origin, false, condition, thenBranch, elseBranch);
        } else if (expr instanceof JCTree.JCSwitchExpression switchExpr) {
            return toExpr(switchExpr);
        } else if (expr instanceof JCTree.JCUnary unary) {
            var innerExpr = toExpr(unary.getExpression());
            switch(unary.getTag()) {
                case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                    reportError(expr, "mutatingExpression", unary.getOperator().name.toString());
                    return getHole(origin);
                }
                case JCTree.Tag.NOT -> {
                    return new UnaryOpExpr(origin, innerExpr, UnaryOpExprOpcode.Not);
                }
                case JCTree.Tag.NEG -> {
                    return new NegationExpression(origin, innerExpr);
                }
                case JCTree.Tag.POS -> {
                    return innerExpr;
                }
                default -> {
                    reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return getHole(origin);
                }
            }
        } else if (expr instanceof JCTree.JCBinary binary) {
            var left = toExpr(binary.getLeftOperand());
            var right = toExpr(binary.getRightOperand());
            Symbol.OperatorSymbol operator = binary.getOperator();
            return translateBinary(binary, binary.type, binary.getLeftOperand().type, operator, left, right);
        } else if (expr instanceof JCTree.JCIdent identifier) {
            if (identifier.sym.name.contentEquals("this")) {
                return new ThisExpr(origin);
            }
            return new NameSegment(origin, identifier.sym.name.toString(), null);
        } else if (expr instanceof JCTree.JCLiteral literal) {
            if (literal.typetag == TypeTag.BOOLEAN) {
                return new LiteralExpr(toOrigin(literal), literal.getValue());
            }
            if (literal.typetag == TypeTag.CHAR) {
                return new CharLiteralExpr(toOrigin(literal), literal.getValue());
            }
            return new LiteralExpr(origin, literal.getValue());
        } else if (expr instanceof JCTree.JCMethodInvocation invocation) {
            var jverifyMethodExpr = jverifyLibMethodToExpr(invocation);
            if (jverifyMethodExpr != null) {
                return jverifyMethodExpr;
            }

            var target = toExpr(invocation.getMethodSelect());
            var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
            return new ApplySuffix(origin, target, null,
                    new ActualBindings(argBindings),null);
        } else if (expr instanceof JCTree.JCFieldAccess fieldAccess) {
            var selectedExpr = toExpr(fieldAccess.selected);
            // TODO does this work if the selected expression isn't trivially of array type?
            if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.sym.name.contentEquals("length")) {
                return new ExprDotName(origin, selectedExpr, getName(fieldAccess, "Length"), null);
            }
            
            if (isEnum(fieldAccess.selected)) {
                return new ApplySuffix(origin, new NameSegment(origin, fieldAccess.sym.name.toString(), null),
                        null, new ActualBindings(List.of()), null);
            } else {
                return new ExprDotName(origin, toExpr(fieldAccess.selected), getName(fieldAccess, fieldAccess.sym.name), null);
            }
        } else if (expr instanceof JCTree.JCArrayAccess arrayAccess) {
            var arrayExpr = toExpr(arrayAccess.getExpression());
            var indexExpr = toExpr(arrayAccess.getIndex());
            return new SeqSelectExpr(origin, true, arrayExpr, indexExpr, null, null);
        } else if (expr instanceof JCTree.JCParens parens) {
            return toExpr(parens.getExpression());
        } else if (expr instanceof JCTree.JCAssignOp assignOp) {
            reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
            return getHole(origin);
        }
        else if (expr instanceof JCTree.JCInstanceOf instanceOf) {
            var expression = toExpr(instanceOf.getExpression());
            var jcType = toType(instanceOf.getType());
            return new TypeTestExpr(origin, expression, jcType);
        }
        reportError(expr, "notSupported", "toExpr: " + expr.getClass().getSimpleName());
        return getHole(origin);  
    }

    public Expression translateBinary(JCTree node,
                                      com.sun.tools.javac.code.Type resultType,
                                      com.sun.tools.javac.code.Type leftType,
                                      Symbol.OperatorSymbol operator, Expression left, Expression right) {
        var origin = toOrigin(node);
        if (leftType.getTag() == TypeTag.FLOAT || leftType.getTag() == TypeTag.DOUBLE) {
            reportError(node, "notSupported", "operator " + operator);
        }
        var isBitwise = switch (operator.name.toString()) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;        
        };

        if (isBitwise) {
            reportError(node, "notSupported", "operator " + operator);
            return getHole(origin);
        }
        BinaryExprOpcode dafnyOperator = toDafny(operator);
        if (dafnyOperator == null) {
            reportError(node, "notSupported", "operator " + operator);
            return getHole(origin);
        }
        return new BinaryExpr(origin, dafnyOperator, left, right);
    }

    private boolean isEnum(JCTree.JCExpression selected) {
        if (selected instanceof JCTree.JCIdent jcIdent) {
            if (jcIdent.sym instanceof Symbol.ClassSymbol classSymbol) {
                return isEnum(classSymbol.type);
            }
        }
        return false;
    }

    /**
     * Translates the specified library method invocation to a Dafny expression,
     * or returns {@code null} if the invocation is not a JVerify library method.
     *
     * <p>Note: header methods like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link MethodCompiler#translateStatement(JCTree.JCStatement)},
     * not here.
     */
    private @Nullable Expression jverifyLibMethodToExpr(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return null;
        }

        var receiver = invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess
                ? fieldAccess.selected
                : null;
        var methodName = jverifyMethod.getQualifiedName().toString();
        var args = invocation.getArguments();
        switch (methodName) {
            case "forall", "exists" -> {
                if (args.size() != 1) {
                    throw new JavaViolationException("A %s call must have exactly one argument".formatted(methodName));
                }
                if (!(args.getFirst() instanceof JCTree.JCLambda lambda)) {
                    reportError(args.getFirst(), "argumentMustBeLambda", methodName);
                    return null;
                }
                var origin = toOrigin(lambda.getBody());
                var boundVars = lambda.params.stream().map(param -> {
                    var paramOrigin = toOrigin(lambda);
                    var paramName = new Name(paramOrigin, param.getName().toString());
                    var paramType = toType(param.getType(), false, paramOrigin);
                    return new BoundVar(paramOrigin, paramName, paramType, false);
                }).toList();
                var body = toExpr(lambda.getBody());
                if (body == null) {
                    return null;
                }
                if ("forall".equals(methodName)) {
                    return new ForallExpr(origin, boundVars, null, body, null);
                } else {
                    return new ExistsExpr(origin, boundVars, null, body, null);
                }
            }
            case "sequence" -> {
                // array conversion to sequence by appending "[..]", optionally with lo/hi
                var array = args.get(0);
                var fromIndex = args.length() > 1 ? args.get(1) : null;
                var toIndex = args.length() > 2 ? args.get(2) : null;
                return toSubsequence(array, fromIndex, toIndex);
            }
            case "drop" -> {
                return toSubsequence(receiver, args.getFirst(), null);
            }
            case "take" -> {
                return toSubsequence(receiver, null, args.getFirst());
            }
            case "subsequence" -> {
                return toSubsequence(receiver, args.get(0), args.get(1));
            }
            case "contains" -> {
                var element = toExpr(args.getFirst());
                var seq = toExpr(receiver);
                return new BinaryExpr(toOrigin(invocation), BinaryExprOpcode.In, element, seq);
            }
            case "old" -> {
                var element = toExpr(args.getFirst());
                return new OldExpr(toOrigin(invocation), element, null);
            }
            case "fresh" -> {
                var element = toExpr(args.getFirst());
                return new FreshExpr(toOrigin(invocation), element, null);
            }
        }

        reportError(invocation.getMethodSelect(), "notSupported", "library method %s".formatted(jverifyMethod));
        return null;
    }

    private SeqSelectExpr toSubsequence(JCTree.JCExpression seqOrArray, JCTree.@Nullable JCExpression lo, JCTree.@Nullable JCExpression hi) {
        var origin = toOrigin(seqOrArray);
        var seqOrArrayExpr = toExpr(seqOrArray);
        var loExpr = lo == null ? null : toExpr(lo);
        var hiExpr = hi == null ? null : toExpr(hi);
        return new SeqSelectExpr(origin, false, seqOrArrayExpr, loExpr, hiExpr, null);
    }

    @Nullable BinaryExprOpcode toDafny(Symbol.OperatorSymbol operator) {
        return switch (operator.name.toString()) {
            case "-" -> BinaryExprOpcode.Sub;
            case "+" -> BinaryExprOpcode.Add;
            case "*" -> BinaryExprOpcode.Mul;
            case "/" -> BinaryExprOpcode.Div;
            case "==" -> BinaryExprOpcode.Eq;
            case "!=" -> BinaryExprOpcode.Neq;
            case "<" -> BinaryExprOpcode.Lt;
            case "<=" -> BinaryExprOpcode.Le;
            case ">" -> BinaryExprOpcode.Gt;
            case ">=" -> BinaryExprOpcode.Ge;
            case "||" -> BinaryExprOpcode.Or;
            case "&&" -> BinaryExprOpcode.And;
            case "%" -> BinaryExprOpcode.Mod;
            case "&" -> BinaryExprOpcode.BitwiseAnd;
            case "|" -> BinaryExprOpcode.BitwiseOr;
            case "^" -> BinaryExprOpcode.BitwiseXor;
            default -> null;
        };
    }

    private Expression toExpr(JCTree.JCSwitchExpression switchExpr) {
        var origin = toOrigin(switchExpr);

        // A switch block consists of either *switch rules* (label -> body)
        // or *switch labeled statement groups* (label: {label:} stmts).
        // Unlike switch rules, switch labeled statement groups automatically "fall through" without break statements,
        // but Dafny's match statement/expression can't express that easily.
        // So for now we only support switch blocks using switch rules.
        if (switchExpr.getCases().getFirst().getCaseKind().equals(JCTree.JCCase.STATEMENT)) {
            reportError(switchExpr, "notSupported", "switch labeled statement group");
            return getHole(origin);
        }

        // Each case has a *switch label*, which is either a *default label* or a *case label*.
        // A *case label* consists of either:
        //  - a list of *case constants*
        //  - a null literal (which the javac AST treats like another case constant)
        //  - a *case pattern* (not supported)
        var translatedCases = new ArrayList<NestedMatchCaseExpr>();
        for (var cas : switchExpr.getCases()) {
            // note: if the case label is a null literal, this is the singleton list of the null literal
            var caseConstants = cas.getExpressions();
            var defaultLabel = cas.getLabels().stream()
                    .filter(label -> label instanceof JCTree.JCDefaultCaseLabel)
                    .findFirst();

            final ExtendedPattern translatedPattern;
            if (caseConstants.nonEmpty()) {
                var literals = caseConstants.stream().map(labelExpr -> {
                    var labelOrigin = toOrigin(labelExpr);
                    final LiteralExpr litExpr;
                    if (labelExpr instanceof JCTree.JCLiteral) {
                        litExpr = (LiteralExpr)toExpr(labelExpr);
                    } else {
                        reportError(labelExpr, "notSupported", "non-literal case constant");
                        litExpr = getHole(labelOrigin);
                    }
                    return (ExtendedPattern) new LitPattern(labelOrigin, false, litExpr);
                }).toList();
                translatedPattern = new DisjunctivePattern(toOrigin(cas), false, literals);
            } else if (defaultLabel.isPresent()) {
                var labelOrigin = toOrigin(defaultLabel.get());
                translatedPattern = new IdPattern(labelOrigin, false, "_", null, null, false);
            } else {
                reportError(cas, "notSupported", "case pattern");
                translatedPattern = null;
            }

            final Expression translatedBody;
            if (cas.getBody() instanceof JCTree.JCExpression) {
                translatedBody = toExpr(cas.getBody());
            } else {
                var bodyKind = cas.getBody() instanceof JCTree.JCBlock ? "block" : "throw statement";
                reportError(cas, "notSupported", "switch rule %s".formatted(bodyKind));
                translatedBody = null;
            }

            if (translatedPattern == null || translatedBody == null) {
                return getHole(origin);
            } else {
                translatedCases.add(new NestedMatchCaseExpr(toOrigin(cas), translatedPattern, translatedBody, null));
            }
        }

        var source = toExpr(switchExpr.getExpression());
        return new NestedMatchExpr(origin, source, translatedCases, true, null);
    }

    private @Nullable Type toType(JCTree tree) {
        return toType(tree, false, null);
    }

    private @Nullable Type toType(JCTree tree, boolean isNullable) {
        return toType(tree, isNullable, null);
    }

    @Nullable
    public Type toType(JCTree tree, boolean isNullable, @Nullable IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> toOrigin(tree));
        var nullableSuffix = isNullable ? "?" : "";

        var primitiveTypeKind = toPrimitiveTypeModuloBoxing(tree);
        if (primitiveTypeKind != null) {
            switch (primitiveTypeKind) {
                case VOID -> {
                    return null;
                }
                case BOOLEAN -> {
                    return new BoolType(origin);
                }
                case INT, SHORT, LONG -> {
                    var mirrors = tree.type.getAnnotationMirrors();
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
                            reportError(tree, "unboundedNonInt", tree.toString());
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
                    return new UserDefinedType(origin, new NameSegment(origin, "Float", null));
                }
                case DOUBLE -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "Double", null));
                }
            }


            reportError(tree, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
            return null;
        } else if (tree instanceof JCTree.JCArrayTypeTree arrayTypeTree) {
            var elemType = toType(arrayTypeTree.getType(), true, originOverride);
            if (elemType == null) {
                // should be unreachable
                throw new IllegalArgumentException("Array type without element type");
            }
            return new UserDefinedType(origin, new NameSegment(origin, "array" + nullableSuffix, List.of(elemType)));
        } else if (tree instanceof JCTree.JCExpression expr) {
            var expression = toExpr(expr);
            
            // Remove the name qualification because we do not support that yet
            Expression nameSegment;
            if (expression instanceof ExprDotName exprDotName) {
                nameSegment = new NameSegment(exprDotName.getOrigin(), exprDotName.getSuffixNameNode().getValue(), null);
            } else {
                nameSegment = expression;
            }
            if (isNullable && nameSegment instanceof NameSegment ns) {
                nameSegment = new NameSegment(ns.getOrigin(), ns.getName() + nullableSuffix, ns.getOptTypeArguments());
            }
            return new UserDefinedType(origin, nameSegment);
        }

        reportError(tree, "notSupported", "for type: " + tree.getClass().getSimpleName());
        return null;
    }

    /**
     * If the specified tree represents either a primitive type or a boxed primitive type,
     * returns the corresponding {@link TypeKind},
     * otherwise returns {@code null}.
     */
    private @Nullable TypeKind toPrimitiveTypeModuloBoxing(JCTree tree) {
        if (tree instanceof JCTree.JCPrimitiveTypeTree primitiveTypeTree) {
            return primitiveTypeTree.getPrimitiveTypeKind();
        } else if (tree instanceof JCTree.JCIdent ident
                && ident.sym.packge().getQualifiedName().contentEquals("java.lang")) {
            var name = ident.sym.getSimpleName().toString();
            if (name.equals(Boolean.class.getSimpleName())) return TypeKind.BOOLEAN;
            if (name.equals(Byte.class.getSimpleName())) return TypeKind.BYTE;
            if (name.equals(Short.class.getSimpleName())) return TypeKind.SHORT;
            if (name.equals(Integer.class.getSimpleName())) return TypeKind.INT;
            if (name.equals(Long.class.getSimpleName())) return TypeKind.LONG;
            if (name.equals(Character.class.getSimpleName())) return TypeKind.CHAR;
            if (name.equals(Float.class.getSimpleName())) return TypeKind.FLOAT;
            if (name.equals(Double.class.getSimpleName())) return TypeKind.DOUBLE;
        }

        return null;
    }

    Name getName(JCTree tree, com.sun.tools.javac.util.Name name) {
        return getName(tree, name.toString());
    }
    
    Name getName(JCTree tree, String name) {
        int startPos = getStartPos(tree);
        var startToken = toToken(startPos);
        var endToken = toToken(startPos + name.length());
        var origin = startToken == null ? contextOrigins.peek() : new TokenRangeOrigin(startToken, endToken);
        return new Name(origin, name);
    }

    private IOrigin declToOrigin(JCTree node, Name name) {
        var entireRange = toOrigin(node);
        return new SourceOrigin(originToRange(entireRange), originToRange(name.getOrigin()));
    }
    
    public IOrigin toOrigin(JCTree node) {
        var startToken = toToken(TreeInfo.getStartPos(node));
        if (startToken == null) {
            return contextOrigins.peek();
        }
        int endPos = getEndPos(node);
        var endToken = endPos == Position.NOPOS ? toToken(TreeInfo.getStartPos(node) + 1) : toToken(endPos);
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
    
    private int getStartPos(JCTree tree) {
        return switch(tree) {
            case JCTree.JCClassDecl classDecl -> getClassNamePosition(classDecl);
            case JCTree.JCMethodDecl methodDecl -> getMethodNamePosition(methodDecl);
            default -> tree.getStartPosition();
        };
    }

    private int getMethodNamePosition(JCTree.JCMethodDecl methodDecl) {
        CharSequence source;
        try {
            source = compilationUnit.getSourceFile().getCharContent(true);
        } catch (IOException e) {
            return Position.NOPOS;
        }
        String sourceText = source.toString();

        int pos = methodDecl.pos;

        if (methodDecl.mods != null && methodDecl.mods.pos != Position.NOPOS) {
            // not sure if this statement is ever useful
            pos = Math.max(pos, getEndPos(methodDecl.mods));
        }

        String methodName = methodDecl.sym.name.toString();
        boolean isConstructor = TreeInfo.isConstructor(methodDecl);

        if (isConstructor) {
            if (methodDecl.typarams != null && !methodDecl.typarams.isEmpty()) {
                JCTree.JCTypeParameter last = methodDecl.typarams.last();
                pos = getEndPos(last);
            }
            return pos;
        } else {
            if (methodDecl.typarams != null && !methodDecl.typarams.isEmpty()) {
                pos = getEndPos(methodDecl.typarams.last());
            }

            if (methodDecl.restype != null) {
                pos = getEndPos(methodDecl.restype);
            }
        }

        while (pos < sourceText.length()) {
            while (pos < sourceText.length() && Character.isWhitespace(sourceText.charAt(pos))) {
                pos++;
            }

            if (pos + methodName.length() <= sourceText.length() &&
                    sourceText.regionMatches(pos, methodName, 0, methodName.length())) {
                // Verify this is the actual method name by checking if it's followed by a '('
                int nextCharPos = pos + methodName.length();
                while (nextCharPos < sourceText.length() && Character.isWhitespace(sourceText.charAt(nextCharPos))) {
                    nextCharPos++;
                }
                if (nextCharPos < sourceText.length() && sourceText.charAt(nextCharPos) == '(') {
                    return pos;
                }
            }

            pos++;
        }

        return Position.NOPOS;
    }

    private int getEndPos(JCTree node) {
        return TreeInfo.getEndPos(node, compilationUnit.endPositions);
    }

    private int getClassNamePosition(JCTree.JCClassDecl classDecl) {
        CharSequence source;
        try {
            source = this.compilationUnit.getSourceFile().getCharContent(true);
        } catch (IOException e) {
            return Position.NOPOS;
        }

        int pos = classDecl.pos;

        if (classDecl.mods != null && classDecl.mods.pos != Position.NOPOS) {
            // Not sure if this statement is useful
            pos = Math.max(pos, getEndPos(classDecl.mods));
        }

        // Find the class/interface/enum/record keyword and skip it
        String sourceText = source.toString();
        String[] keywords = {"class", "interface", "enum", "record"};

        for (String keyword : keywords) {
            int keywordPos = sourceText.indexOf(keyword + " ", pos);
            if (keywordPos >= pos) {
                // Found the keyword, the class name starts after it and any whitespace
                int nameStart = keywordPos + keyword.length();
                while (nameStart < sourceText.length() && Character.isWhitespace(sourceText.charAt(nameStart))) {
                    nameStart++;
                }

                // Verify we've found the correct position by checking if the text matches the class name
                String className = classDecl.name.toString();
                if (sourceText.regionMatches(nameStart, className, 0, className.length())) {
                    return nameStart;
                }
            }
        }

        return Position.NOPOS;
    }
        
    
    private Token toToken(int pos) {
        if (pos == Position.NOPOS) {
            return null;
        }
        return new Token(
                compilationUnit.getLineMap().getLineNumber(pos),
                compilationUnit.getLineMap().getColumnNumber(pos) + 1);
    }

    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.outermostClass().className().contentEquals(JVERIFY_CLASS);
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


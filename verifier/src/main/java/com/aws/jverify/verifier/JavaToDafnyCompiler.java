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
import com.sun.tools.javac.code.Types;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public final Context context;
    List<DatatypeDecl> lambdaDatatypeDecls = new ArrayList<>();
    Stack<IOrigin> contextOrigins = new Stack<>();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    private NameMangler nameMangler = new NameMangler();

    JCTree.JCCompilationUnit compilationUnit;
    private JCDiagnostic.Factory diagnosticFactory;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;
    private final Map<Symbol.ClassSymbol, ExternalTypeContract> externalContracts = new HashMap<>();


    public JavaToDafnyCompiler(Context context, VerifierOptions verifierOptions) {
        this.context = context;
        shouldVerifies.push(verifierOptions.verifyByDefault()
                ? ShouldVerifyMode.DefaultYes
                : ShouldVerifyMode.DefaultNo);
    }
    
    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, List<JavaFileObject> files) {
        JavacTool compiler = JavacTool.create();

        // don't assume the argument is modifiable
        files = new ArrayList<>(files);
        files.add(new SourceFile("builtin-contracts.java", Common.getResourceFile(getClass(), builtinFile)));

        var classpathEntries = new ArrayList<Path>();
        
        for(var extraPath : options.extraClassPathEntries()) {
            if (!Files.exists(extraPath.toAbsolutePath())) {
                throw new IllegalArgumentException("Could not find file: " + extraPath);
            }
        }
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
        for (var compilationUnit : parsed) {
            findExternalContracts((JCTree.JCCompilationUnit) compilationUnit);
        }
        for (var compilationUnit : parsed) {
            var fileStart = translateFile((JCTree.JCCompilationUnit) compilationUnit);
            filesStarts.add(fileStart);
        }

        return new FilesContainer(filesStarts);
    }
    
    record ExternalTypeContract(Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts) {
        
    }
    private void findExternalContracts(JCTree.JCCompilationUnit compilationUnit) {
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
                continue;
            }
            
            var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
            if (contracteeSymbol == null) {
                reportError(classDecl, "noContractTarget", classDecl.name.toString());
                continue;
            }
            if (externalContracts.containsKey(contracteeSymbol)) {
                reportError(contractAnnotation, "duplicateContract", contracteeSymbol.name);
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

                var methodAnnotations = methodDecl.getModifiers().getAnnotations();
                var methodAnnotationsByName = methodAnnotations.stream().collect(Collectors.toMap(
                        (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                        a -> a));
                
                var methodSymbol = methodDecl.sym;
                var baseMethod = OverrideFinder.findOverriddenMethod(methodSymbol, Types.instance(context));
                if (baseMethod != null) {
                    var methodCompiler = new MethodCompiler(this);
                    var isPure = methodAnnotationsByName.containsKey(Pure.class.getName());
                    var header = new MethodOrLoopContract(methodDecl, isPure);
                    methodCompiler.translateHeader(methodDecl.getBody(), header);
                    externalContracts.put(baseMethod, header);
                } else {
                    if (typeHasSource(contracteeSymbol) && !isSynthetic(methodDecl, methodSymbol)) {
                        // For static members, we currently do not check whether they occur in the contractee
                        reportError(methodDecl, "unusedContractMethod", methodDecl.name);
                    }
                }
            }
            this.externalContracts.put(contracteeSymbol, new ExternalTypeContract(externalContracts));
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



    private FileStart translateFile(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        this.lambdaDatatypeDecls.clear();

        ArrayList<TopLevelDecl> topLevelDecls = new ArrayList<>();
        Stack<Tree> remainingTypes = new Stack<>();
        remainingTypes.addAll(compilationUnit.getTypeDecls());
        while(!remainingTypes.isEmpty()) {
            var typeDecl = remainingTypes.pop();
            var dafnyDecls = translateTypeDeclaration(typeDecl, remainingTypes);
            topLevelDecls.addAll(dafnyDecls);
        }

        topLevelDecls.addAll(0, lambdaDatatypeDecls);
        lambdaDatatypeDecls.clear();

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

    List<Symbol.MethodSymbol> invariants = new ArrayList<>();
    List<JCTree.JCVariableDecl> initializers = new ArrayList<>();
    
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
    
    @Nullable List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree, Stack<Tree> nestedTypes) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));

            processVerifyAnnotation(annotationsByName);

            Name name = getName(classDecl, this.nameMangler.mangleSymbolName(classDecl.sym));
            if (externalContracts.containsKey(classDecl.sym)) {
                boolean isConcrete = !isInterfaceOrAbstract(classDecl.sym);
                if (isConcrete) {
                    reportError(name.getOrigin(), "concreteTypeWithExternalContract", classDecl.name);
                }
            }
            var origin = declToOrigin(classDecl, name);
            contextOrigins.push(origin);

            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                var contractee = getContractTarget(classDecl, contractAnnotation);
                if (contractee != null) {
                    typeForWhichCurrentClassIsDefiningContract = contractee;
                    name = new Name(name.getOrigin(), nameMangler.mangleSymbolName(typeForWhichCurrentClassIsDefiningContract));
                }
            }

            List<? extends TopLevelDecl> result;
            if (isEnum(classDecl.type)) {
                result = List.of(translateEnum(classDecl, origin, name));
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
    
    private List<ClassLikeDecl> translateClass(Stack<Tree> nestedTypes, JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
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

        var createTrait = isInterfaceOrAbstract(getCurrentTypeSymbol(classDecl));
        
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
            if (!(member instanceof JCTree.JCVariableDecl variableDecl)) {
                var dafnyMember = translateMember(member, nestedTypes);
                if (dafnyMember != null) {
                    members.add(dafnyMember);
                }
            }
        }
        var definingSymbol = getCurrentTypeSymbol(classDecl);
        if (typeForWhichCurrentClassIsDefiningContract != null && typeHasSource(typeForWhichCurrentClassIsDefiningContract)) {
            // If the contractee has source, then we have a merged contract
            // And we do not need to traverse the contracter.
            // The contractee will lookup contracts in the contractor
            // for bodyless members
            return null;
        }
        
        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null)
        {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }
        var superTraits = baseTypes.
                filter(type -> typeHasAContract(type)).
                map((com.sun.tools.javac.code.Type type) -> translateType(type, false, origin)).
                collect(Collectors.<Type>toList());
        
        var typeParameters = translateTypeParameters(classDecl.typarams);
        if (createTrait) {
            if (classDecl.getModifiers().getAnnotations().stream().
                    anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident &&
                            ident.name.contentEquals("Modifiable"))) {
                superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "object", null)));
            }
            return List.of(new TraitDecl(origin, name, null, typeParameters, members, superTraits, false));
        } else {
            var trait = new TraitDecl(origin, name, null, typeParameters, members, superTraits, false);
            List<MemberDecl> constructors = List.of();
            List<Type> typeArgs = List.of();
            var clazz = new ClassDecl(origin, new Name(name.getOrigin(), "_Class_" + name.getValue()), null,
                    typeParameters, constructors, List.of(new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs))), false);
            return List.of(trait, clazz);
        }
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
            if (!p.bounds.isEmpty()) {
               reportError(p, "notSupported", "type bounds");
            }
            var bounds = p.bounds.map(this::translateType);
            return new TypeParameter(toOrigin(p),
                    name, null, TPVarianceSyntax.NonVariant_Strict,
                    new TypeParameterCharacteristics(
                            TypeParameterEqualitySupportValue.InferredRequired,
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
                var variableName = nameMangler.mangleSymbolName(variableDecl.sym);
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
        Name fieldName = getName(variableDecl, nameMangler.mangleSymbolName(variableDecl.sym));
        IOrigin origin = declToOrigin(variableDecl, fieldName);
        Type type = translateType(variableDecl.vartype.type, isNullable(variableDecl.getModifiers()), toOrigin(variableDecl.vartype));
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

    private boolean isNullable(com.sun.tools.javac.code.Type type) {
        if (type.getAnnotation(com.aws.jverify.Nullable.class) != null) {
            return true;
        }

        if (type instanceof com.sun.tools.javac.code.Type.ArrayType arrayType && isNullable(arrayType.elemtype)) {
            return true;
        }

        return false;
    }

    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        return translateMethodOrLambda(method, method.getModifiers(), method.sym, method.body, method.typarams);
    }

    /**
     * @param sourceBody Either a JCBlock or a JCExpression. The latter is for the benefit of lambda translation.
     */
    private @Nullable MethodOrFunction translateMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers, 
                                                               Symbol.MethodSymbol methodSymbol, 
                                                               JCTree sourceBody,
                                                               List<JCTree.JCTypeParameter> typeParameters
    ) { 
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
            return translatePureMethodOrLambda(source, modifiers, methodSymbol, sourceBody, typeParameters, shouldVerify);
        } else {
            return translateImpureMethodOrLambda(source, modifiers, methodSymbol, sourceBody, typeParameters, shouldVerify);
        }
    }

    private MethodOrConstructor translateImpureMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                              Symbol.MethodSymbol methodSymbol, JCTree sourceBody,
                                                              List<JCTree.JCTypeParameter> typeParameters,
                                                              boolean shouldVerify) {
        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var bodyOrigin = toOrigin(sourceBody);

        var dafnyTypeParameters = translateTypeParameters(typeParameters);

        var methodCompiler = new MethodCompiler(this);
        var name = getName(source, nameMangler.mangleSymbolName(methodSymbol));
        var origin = declToOrigin(source, name);
        var isStatic = isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol, origin);

        MethodOrLoopContract header;
        List<Statement> bodyStatements = null;
        if (sourceBody instanceof JCTree.JCExpression expressionBody) {
            if (shouldVerify) {
                bodyStatements = List.of(
                        new ReturnStmt(bodyOrigin, null, List.of(
                                new ExprRhs(bodyOrigin, null, toExpr(expressionBody)))));
            }
            header = externalContract;
            if (header == null) {
                header = new MethodOrLoopContract(source, false);
            }
        } else {
            if (sourceBody == null) {
                header = externalContract;
                if (header == null) {
                    return null;
                }
            } else {
                if (!(source instanceof JCTree.JCLambda) && externalContract != null) {
                    reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                header = new MethodOrLoopContract(source, false);
                List<JCTree.JCStatement> postHeader = methodCompiler.translateHeader(((JCTree.JCBlock) sourceBody).stats, header);
                if (shouldVerify) {
                    bodyStatements = methodCompiler.translateStatements(postHeader);
                }
            }
        }
        applyInvariants(sourceBody, modifiers, methodSymbol, header);
        methodCompiler.checkEmptyExpressions(source, header.invariants, "invariants", "method");

        if (header.returnNames.size() > 1) {
            reportError(source, "multipleReturnNames");
            return null;
        }
        var outs = new ArrayList<Formal>();
        if (methodSymbol.type.getReturnType() != null) {
            var returnType = translateType(methodSymbol.type.getReturnType(), bodyOrigin);
            if (returnType != null) {
                Name returnName;
                if (header.returnNames.size() == 1) {
                    returnName = header.returnNames.getFirst();
                } else {
                    returnName = new Name(origin, "r");
                }
                outs.add(new Formal(origin, returnName, returnType,
                        false, false, null, null, false, false, false, null));
            }
        }

        if (isConstructor(methodSymbol)) {
            var containerIsInterface = typeForWhichCurrentClassIsDefiningContract != null &&
                    isInterface(typeForWhichCurrentClassIsDefiningContract);
            if (containerIsInterface) {
                var synthetic = isSynthetic(source, methodSymbol);
                if (synthetic) {
                    // ignore default constructors in interfaces classes
                    return null;
                } else {
                    return null;
                }
            }
            
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

    private boolean isSynthetic(JCTree source, Symbol.MethodSymbol methodSymbol) {
        var containerPos = JavacTrees.instance(context).getTree(methodSymbol.enclClass()).pos;
        var synthetic = source.pos == containerPos;
        return synthetic;
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
                                                 List<JCTree.JCTypeParameter> typeParameters, boolean shouldVerify) {
        var bodyOrigin = toOrigin(sourceBody);

        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var methodCompiler = new MethodCompiler(this);
        var name = getName(source, nameMangler.mangleSymbolName(methodSymbol));
        var origin = declToOrigin(source, name);
        var isStatic = isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol, origin);
        Expression body = null;
        MethodOrLoopContract header;
        var returnType = translateType(methodSymbol.type.getReturnType(), bodyOrigin);
        if (returnType == null) {
            reportError(source, "pureMethodsNeedsReturnType");
            return null;
        }
        if (sourceBody instanceof JCTree.JCExpression) {
            if (shouldVerify) {
                body = toExpr((JCTree.JCExpression) sourceBody);
            }

            header = externalContract;
            if (header == null) {
                header = new MethodOrLoopContract(source, true);
            }
        } else {
            if (sourceBody == null) {
                header = externalContract;
            } else {
                if (!(source instanceof JCTree.JCLambda) && externalContract != null) {
                    reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                header = new MethodOrLoopContract(source, true);
                var postHeader = methodCompiler.translateHeader((JCTree.JCBlock) sourceBody, header);
                if (postHeader.size() != 1) {
                    reportError(source, "pureMethodMultipleStatements");
                    return null;
                }

                var statement = postHeader.getFirst();
                if (shouldVerify) {
                    if (statement instanceof JCTree.JCReturn returnStatement) {
                        body = toExpr(returnStatement.expr);
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
                header.getDecreases(), isStatic, false, null, returnType,
                body, null, null);
    }

    private boolean processVerifyAnnotationAndPop(Map<String, JCTree.JCAnnotation> annotationsByName) {
        processVerifyAnnotation(annotationsByName);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        return shouldVerify;
    }

    private List<Formal> getIns(Symbol.MethodSymbol methodSymbol, IOrigin origin) {
        return methodSymbol.getParameters().map(jvd -> {
            Name formalName = new Name(origin, jvd.name.toString());
            var syntacticType = translateType(jvd.type, origin);
            return new Formal(origin, formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        });
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
        if (isPublic) {
            for(var invariant : invariants) {
                var memberName = nameMangler.mangleSymbolName(invariant);
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

    public Expression toExpr(JCTree tree) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExpr(expression);
        }
        reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return getHole(toOrigin(tree));
    }

    static LiteralExpr getHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, true);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr) {
        return toAssignmentRhs(expr, null);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> toOrigin(expr));
        switch (expr) {
            case JCTree.JCNewClass newClass -> {
                var argBindings = newClass.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
                String ctorNameStr = nameMangler.mangleSymbolName(newClass.constructor);
                Name ctorName = new Name(origin, ctorNameStr);
                var baseType = toExpr(newClass.clazz);
                var ty = new UserDefinedType(origin, new ExprDotName(origin, baseType, ctorName, null));

                return new AllocateClass(origin, null, ty, new ActualBindings(argBindings));
            }
            case JCTree.JCNewArray newArray -> {
                var arrayDimensions = newArray.getDimensions().stream().map(d -> toExpr(d)).toList();
                var arrayInitializers = newArray.getInitializers();
                var arrayJavaType = newArray.getType();
                if (arrayJavaType instanceof JCTree.JCArrayTypeTree _) {
                    reportError(expr, "notSupported", "multi-dimensional arrays");
                }
                var arrayDafnyType = translateType(arrayJavaType.type, true, toOrigin(arrayJavaType));

                if (arrayInitializers != null && !arrayInitializers.isEmpty()) {
                    reportError(expr, "notSupported", "new array with initializers");
                }
                return new AllocateArray(origin, null, arrayDafnyType, arrayDimensions, null);
            }
            case null, default -> {
            }
        }
        var dafnyExpr = toExpr(expr, originOverride);
        return new ExprRhs(origin, null, dafnyExpr);
    }

    public Expression toExpr(JCTree.JCExpression expr) {
        return toExpr(expr, null);
    }

    public Expression toExpr(JCTree.JCExpression expr, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> toOrigin(expr));
        switch (expr) {
            case JCTree.JCConditional conditional -> {
                var condition = toExpr(conditional.getCondition());
                var thenBranch = toExpr(conditional.getTrueExpression());
                var elseBranch = toExpr(conditional.getFalseExpression());
                return new ITEExpr(origin, false, condition, thenBranch, elseBranch);
            }
            case JCTree.JCSwitchExpression switchExpr -> {
                return translateSwitchExpression(switchExpr);
            }
            case JCTree.JCUnary unary -> {
                var innerExpr = toExpr(unary.getExpression());
                switch (unary.getTag()) {
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
            }
            case JCTree.JCBinary binary -> {
                var left = toExpr(binary.getLeftOperand());
                var right = toExpr(binary.getRightOperand());
                Symbol.OperatorSymbol operator = binary.getOperator();
                return translateBinary(binary, binary.type, binary.getLeftOperand().type, operator, left, right);
            }
            case JCTree.JCIdent identifier -> {
                var identName = nameMangler.mangleSymbolName(identifier.sym);
                if (identName.contentEquals("this")) {
                    return new ThisExpr(origin);
                }
                return new NameSegment(origin, identName, null);
            }
            case JCTree.JCLiteral literal -> {
                if (literal.typetag == TypeTag.BOOLEAN) {
                    return new LiteralExpr(toOrigin(literal), literal.getValue());
                }
                if (literal.typetag == TypeTag.CHAR) {
                    return new CharLiteralExpr(toOrigin(literal), literal.getValue());
                }
                return new LiteralExpr(origin, literal.getValue());
            }
            case JCTree.JCMethodInvocation invocation -> {
                var jverifyMethodExpr = jverifyLibMethodToExpr(invocation);
                if (jverifyMethodExpr != null) {
                    return jverifyMethodExpr;
                }

                var target = toExpr(invocation.getMethodSelect());
                var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
                return new ApplySuffix(origin, target, null,
                        new ActualBindings(argBindings), null);
            }
            case JCTree.JCFieldAccess fieldAccess -> {
                if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
                    return new NameSegment(origin, nameMangler.mangleSymbolName(classSymbol), List.of());
                }
                var selectedExpr = toExpr(fieldAccess.selected);
                // TODO does this work if the selected expression isn't trivially of array type?
                if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
                    return new ExprDotName(origin, selectedExpr, getName(fieldAccess, "Length"), null);
                }
                    
                var fieldName = nameMangler.mangleSymbolName(fieldAccess.sym);
                if (isEnum(fieldAccess.selected)) {
                    return new ApplySuffix(origin, new NameSegment(origin, fieldName, null),
                            null, new ActualBindings(List.of()), null);
                } else {
                    return new ExprDotName(origin, toExpr(fieldAccess.selected), getName(fieldAccess, fieldName), null);
                }
            }
            case JCTree.JCArrayAccess arrayAccess -> {
                var arrayExpr = toExpr(arrayAccess.getExpression());
                var indexExpr = toExpr(arrayAccess.getIndex());
                return new SeqSelectExpr(origin, true, arrayExpr, indexExpr, null, null);
            }
            case JCTree.JCParens parens -> {
                return toExpr(parens.getExpression());
            }
            case JCTree.JCAssignOp assignOp -> {
                reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
                return getHole(origin);
            }
            case JCTree.JCInstanceOf instanceOf -> {
                var expression = toExpr(instanceOf.getExpression());
                var jcType = translateType(instanceOf.getType());
                return new TypeTestExpr(origin, expression, jcType);
            }
            case JCTree.JCTypeCast cast -> {
                var castExpr = toExpr(cast.getExpression());
                var type = translateType(cast.getType());
                return new ConversionExpr(origin, castExpr, type, "");
            }
            case JCTree.JCLambda lambda -> {
                var types = Types.instance(context);
                var methodSymbol = (Symbol.MethodSymbol) types.findDescriptorSymbol(lambda.target.tsym);
                var maker = TreeMaker.instance(context);
                var methodDecl = translateMethodOrLambda(lambda, maker.Modifiers(0), methodSymbol, lambda.getBody(), List.of());

                var datatypeName = "Lambda" + lambdaDatatypeDecls.size();
                var datatypeNameNode = new Name(origin, datatypeName);
                var datatypeCtor = new DatatypeCtor(origin, datatypeNameNode, null, false, List.of());
                var trait = translateType(lambda.target, origin);
                var datatypeDecl = new IndDatatypeDecl(origin, datatypeNameNode, null, List.of(), List.of(methodDecl),
                        List.of(trait), List.of(datatypeCtor), false);
                lambdaDatatypeDecls.add(datatypeDecl);

                // TODO: Using a DatatypeValue directly ends up crashing when printing temp.dfy,
                // because the printer tries to read DatatypeValue.Arguments before it's filled in by resolution.
//                return new DatatypeValue(origin, datatypeName, datatypeName, new ActualBindings(List.of()));
                return new ExprDotName(origin, new NameSegment(origin, datatypeName, null), datatypeNameNode, null);
            }
            case JCTree.JCTypeApply typeApply -> {
                var type = this.toExpr(typeApply.getType());
                if (type instanceof NameSegment nameSegment) {
                    List<Type> arguments;
                    if (typeApply.getTypeArguments().isEmpty()) {
                        // Occurs when the type arguments were inferred
                        arguments = typeApply.type.getTypeArguments().stream().map(t -> translateType(t, false, origin)).toList();
                    } else {
                        arguments = typeApply.getTypeArguments().stream().map(this::translateType).toList();
                    }
                    return new NameSegment(origin, nameSegment.getName(), arguments);
                }
                throw new RuntimeException("All Dafny type references are NameSegments, since we do not use Dafny modules");
            }
            case null, default -> {
            }
        }
        reportError(expr, "notSupported", expr.getClass().getSimpleName());
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
     * <p>Note: header methodContracts like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link MethodCompiler#translateStatement(JCTree.JCStatement)},
     * not here.
     */
    private @Nullable Expression jverifyLibMethodToExpr(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return null;
        }

        var origin = toOrigin(invocation);
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
                var boundVars = lambda.params.stream().map(param -> {
                    var paramOrigin = toOrigin(lambda);
                    var paramName = new Name(paramOrigin, param.getName().toString());
                    var paramType = translateType(param.getType().type, false, paramOrigin);
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
                return toSubsequence(origin, array, fromIndex, toIndex);
            }
            case "drop" -> {
                return toSubsequence(origin, receiver, args.getFirst(), null);
            }
            case "take" -> {
                return toSubsequence(origin, receiver, null, args.getFirst());
            }
            case "subsequence" -> {
                return toSubsequence(origin, receiver, args.get(0), args.get(1));
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

    private SeqSelectExpr toSubsequence(IOrigin origin, JCTree.JCExpression seqOrArray, JCTree.@Nullable JCExpression lo, JCTree.@Nullable JCExpression hi) {
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

    private Expression translateSwitchExpression(JCTree.JCSwitchExpression switchExpr) {
        var origin = toOrigin(switchExpr);
        var patternBodies = translateSwitchLabels(switchExpr);
        if (patternBodies == null) {
            return getHole(origin);
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = toOrigin(patternBody.cas());
            var body = patternBody.body();
            final Expression translatedBody;

            // A switch rule introduces either an expression, a block, or a throw statement.
            if (body instanceof JCTree.JCExpression) {
                translatedBody = toExpr(body);
            } else {
                var bodyKind = body instanceof JCTree.JCBlock ? "block" : "throw statement";
                reportError(body, "notSupported", "switch rule %s".formatted(bodyKind));
                translatedBody = getHole(caseOrigin);
            }
            return new NestedMatchCaseExpr(caseOrigin, patternBody.pattern(), translatedBody, null);
        }).toList();

        var source = toExpr(switchExpr.getExpression());
        return new NestedMatchExpr(origin, source, translatedCases, true, null);
    }

    record SwitchLabelPatternAndBody(JCTree.JCCase cas, ExtendedPattern pattern, JCTree body) {}

    /**
     * Translates the switch labels of the given {@code switch} statement or expression
     * into the corresponding {@link ExtendedPattern}s,
     * and returns the patterns along with their corresponding (untranslated) bodies.
     * Returns {@code null} if an unrecoverable error is reported,
     * such as if the input tree uses unsupported features.
     */
    @Nullable List<SwitchLabelPatternAndBody> translateSwitchLabels(JCTree switchTree) {
        // JCTree is the first common superclass of JCSwitch and JCSwitchExpression,
        // so we settle for dynamically checking that the argument is one of them.
        var cases = switch (switchTree) {
            case JCTree.JCSwitch switchStmt -> switchStmt.getCases();
            case JCTree.JCSwitchExpression switchExpr -> switchExpr.getCases();
            default -> throw new IllegalArgumentException(
                    "Expected switch statement or expression but got " + switchTree.getClass());
        };

        // A switch block consists of either *switch rules* (label -> body)
        // or *switch labeled statement groups* (label: {label:} stmts).
        // Unlike switch rules, switch labeled statement groups automatically "fall through" without break statements,
        // but Dafny's match statement/expression can't express that easily.
        // So for now we only support switch blocks using switch rules.
        if (cases.getFirst().getCaseKind().equals(JCTree.JCCase.STATEMENT)) {
            reportError(switchTree, "notSupported", "switch labeled statement group");
            return null;
        }

        return cases.stream()
                .map(cas -> new SwitchLabelPatternAndBody(cas, translateSwitchLabel(cas), cas.getBody()))
                .toList();
    }

    /**
     * Translates the given switch label into a pattern.
     */
    private ExtendedPattern translateSwitchLabel(JCTree.JCCase cas) {
        // Each case has a *switch label*, which is either a *default label* or a *case label*.
        // A *case label* consists of either:
        //  - a list of *case constants*
        //  - a null literal (which the javac AST treats like another case constant)
        //  - a *case pattern* (not supported)

        // note: if the case label is a null literal, this is the singleton list of the null literal
        var caseConstants = cas.getExpressions();
        var defaultLabel = cas.getLabels().stream()
                .filter(label -> label instanceof JCTree.JCDefaultCaseLabel)
                .findFirst();

        if (caseConstants.nonEmpty()) {
            var literals = caseConstants.stream().map(this::translateCaseConstant).toList();
            return new DisjunctivePattern(toOrigin(cas), false, literals);
        } else if (defaultLabel.isPresent()) {
            return makeWildPattern(toOrigin(defaultLabel.get()));
        } else {
            reportError(cas, "notSupported", "case pattern");
            // Return something sensible
            return makeWildPattern(toOrigin(cas));
        }
    }

    /**
     * Translates the given switch label case constant into a pattern.
     */
    private ExtendedPattern translateCaseConstant(JCTree.JCExpression expr) {
        var origin = toOrigin(expr);
        final LiteralExpr litExpr;
        if (expr instanceof JCTree.JCLiteral) {
            litExpr = (LiteralExpr)toExpr(expr);
        } else {
            reportError(expr, "notSupported", "non-literal case constant");
            litExpr = getHole(origin);
        }
        return new LitPattern(origin, false, litExpr);
    }

    static IdPattern makeWildPattern(IOrigin origin) {
        return new IdPattern(origin, false, "_", null, null, false);
    }

    public @Nullable Type translateType(JCTree tree) {
        return translateType(tree.type, isNullable(tree.type), toOrigin(tree));
    }

    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin) {
        return translateType(type, isNullable(type), origin);
    }

    @Nullable
    public Type translateType(com.sun.tools.javac.code.Type type, boolean isNullable, IOrigin origin) {
        var nullableSuffix = isNullable ? "?" : "";

        var primitiveTypeKind = toPrimitiveTypeModuloBoxing(type);
        if (primitiveTypeKind != null) {
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
                    return new UserDefinedType(origin, new NameSegment(origin, "Float", null));
                }
                case DOUBLE -> {
                    return new UserDefinedType(origin, new NameSegment(origin, "Double", null));
                }
            }


            reportError(origin, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
            return null;
        }

        switch (type) {
            case com.sun.tools.javac.code.Type.ArrayType arrayTypeTree -> {
                // TODO: Assuming nullable here means it's not possible to have non-nullable array elements?
                var elemType = translateType(arrayTypeTree.elemtype, true, origin);
                if (elemType == null) {
                    // should be unreachable
                    throw new IllegalArgumentException("Array type without element type");
                }
                return new UserDefinedType(origin, new NameSegment(origin, "array" + nullableSuffix, List.of(elemType)));
            }
            case com.sun.tools.javac.code.Type.ClassType classType -> {
                // Remove the name qualification because we do not support that yet
                var mangledName = nameMangler.mangleSymbolName(classType.tsym);
                var arguments = classType.getTypeArguments().map(a -> translateType(a, false, origin));
                if (arguments.isEmpty()) {
                    arguments = null;
                }
                Expression nameSegment = new NameSegment(origin, mangledName, arguments);
                if (isNullable && nameSegment instanceof NameSegment ns) {
                    nameSegment = new NameSegment(ns.getOrigin(), ns.getName() + nullableSuffix, ns.getOptTypeArguments());
                }
                return new UserDefinedType(origin, nameSegment);
            }
            case com.sun.tools.javac.code.Type.TypeVar typeVar -> {
                return new UserDefinedType(origin, new NameSegment(origin, nameMangler.mangleSymbolName(typeVar.tsym), null));
            }
            case null, default -> {
            }
        }
        reportError(origin, "notSupported", type.getClass().getSimpleName());
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
        } else if (type instanceof com.sun.tools.javac.code.Type.ClassType classType
                && classType.tsym.packge().getQualifiedName().contentEquals("java.lang")) {
            var name = classType.tsym.getSimpleName().toString();
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

    private static boolean isConstructor(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.name == methodSymbol.name.table.names.init;
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

        String methodName = methodDecl.name.toString();
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


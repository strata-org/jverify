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
    public final NameMangler nameMangler = new NameMangler();
    public final ExpressionCompiler expressionCompiler = new ExpressionCompiler(this);

    JCTree.JCCompilationUnit compilationUnit;
    private JCDiagnostic.Factory diagnosticFactory;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;
    private final Map<Symbol.ClassSymbol, ExternalTypeContract> externalContracts = new HashMap<>();
    boolean buildingTrait;


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
            return List.of();
        }
        
        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null)
        {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }
        var superTraits = baseTypes.
                filter(this::typeHasAContract).
                map((com.sun.tools.javac.code.Type type) -> translateType(type, false, origin)).
                collect(Collectors.<Type>toList());
        
        var typeParameters = translateTypeParameters(classDecl.typarams);
        return buildTraitAndClassTwin(classDecl, origin, name, members, typeParameters, superTraits);
    }

    private static List<ClassLikeDecl> buildTraitAndClassTwin(JCTree.JCClassDecl classDecl,
                                                              IOrigin origin, Name name,
                                                              ArrayList<MemberDecl> members,
                                                              List<TypeParameter> typeParameters,
                                                              List<Type> superTraits) {
        var traitMembers = new ArrayList<MemberDecl>();
        for(var member : members) {
            if (member instanceof Method method && !method.getHasStaticKeyword()) {
                var traitMethod = new Method(origin, new Name(origin, method.getNameNode().getValue()), method.getAttributes(),
                        method.getIsGhost(), method.getSignatureEllipsis(), method.getTypeArgs(), method.getIns(),
                        method.getReq(), method.getEns(), method.getReads(), method.getDecreases(), method.getMod(),
                        method.getHasStaticKeyword(), method.getOuts(), null, method.getIsByMethod());
                traitMembers.add(traitMethod);
            } else if (member instanceof Function function) {
                traitMembers.add(function);
            } else if (member instanceof Constructor constructor) {
                traitMembers.add(constructorToInitMethod(origin, constructor));
            } else {
                traitMembers.add(member);
            }
        }

        if (!isInterface(classDecl.sym) || classDecl.getModifiers().getAnnotations().stream().
                anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident &&
                        ident.name.contentEquals("Modifiable"))) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "object", null)));
        }
        
        var trait = new TraitDecl(origin, name, null, typeParameters, traitMembers, superTraits, false);
        List<Type> typeArgs = typeParameters.stream().map(
                p -> (Type)new UserDefinedType(p.getOrigin(), 
                        new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null))).toList();

        var classNeeded = false;
        var classMembers = new ArrayList<MemberDecl>();
        for(var member : members) {
            if (member instanceof Method method) {
                if (!method.getHasStaticKeyword()) {
                    classMembers.add(member);
                    classNeeded = true;
                }
            } if (member instanceof Function function) {
                if (function.getBody() == null) {
                    classMembers.add(member);
                }
            } else if (member instanceof Constructor constructor) {
                List<Statement> bodyProper = List.of(); // TODO call trait init method.
                List<Statement> bodyInit = List.of(); // TODO auto-init fields?
                DividedBlockStmt body = new DividedBlockStmt(constructor.getOrigin(), null, List.of(), bodyInit, constructor.getOrigin(),
                        bodyProper);
                var classConstructor = new Constructor(constructor.getOrigin(), constructor.getNameNode(), null, false, null, 
                        constructor.getTypeArgs(), constructor.getIns(),
                        constructor.getReq(), constructor.getEns(), constructor.getReads(),
                        constructor.getDecreases(), constructor.getMod(),
                        body);
                classMembers.add(classConstructor);
                classNeeded = true;
            }
        }

        if (classNeeded) {
            var clazz = new ClassDecl(origin, new Name(name.getOrigin(), "_Class_" + name.getValue()), null,
                    typeParameters, classMembers, List.of(new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs))), false);
            return List.of(trait, clazz);
        } else {
            return List.of(trait);
        }
    }

    private static Method constructorToInitMethod(IOrigin type, Constructor constructor) {
        BlockStmt body = new BlockStmt(constructor.getBody().getOrigin(), null, List.of(), 
                constructor.getBody().getBodyInit());
        Name nameNode = new Name(type, "_init_" + constructor.getNameNode().getValue());
        var initMethod = new Method(type, nameNode, constructor.getAttributes(),
                constructor.getIsGhost(), constructor.getSignatureEllipsis(), constructor.getTypeArgs(), constructor.getIns(),
                constructor.getReq(), constructor.getEns(), constructor.getReads(), constructor.getDecreases(), 
                constructor.getMod(),
                false, List.of(), body, false);
        return initMethod;
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
                var rhs = expressionCompiler.toExpr(variableDecl.getInitializer());
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
    @Nullable
    public MethodOrFunction translateMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                    Symbol.MethodSymbol methodSymbol,
                                                    JCTree sourceBody,
                                                    List<JCTree.JCTypeParameter> typeParameters
    ) {
        if (typeForWhichCurrentClassIsDefiningContract != null && isSynthetic(source, methodSymbol)) {
            return null;
        }
        
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
                                new ExprRhs(bodyOrigin, null, expressionCompiler.toExpr(expressionBody)))));
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

            return new Constructor(origin, name , null, false, null, dafnyTypeParameters, ins,
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
                body = expressionCompiler.toExpr((JCTree.JCExpression) sourceBody);
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


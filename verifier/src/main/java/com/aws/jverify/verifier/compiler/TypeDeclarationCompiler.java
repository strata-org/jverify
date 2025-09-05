package com.aws.jverify.verifier.compiler;

import com.aws.jverify.*;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.simplifications.workaround.TraitWithConstructorCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TypeDeclarationCompiler {
    private static final String DAFNY_REFERENCE_BASE_TYPE = "object";
    public final JavaToDafnyCompiler compiler;
    private final VerifyAnnotationCompiler verifyAnnotationCompiler;
    private final Types types;
    private final NameCompiler nameCompiler;
    private final MethodOrLoopContractCompiler methodOrLoopContractCompiler;
    private final Reporter reporter;
    private final JVerifyUtils jverifyUtils;
    JVerifyIndex index;
    private final List<JCTree.JCMethodDecl> invariants = new ArrayList<>();
    private final List<JCTree.JCVariableDecl> initializers = new ArrayList<>();
    public final Map<Symbol.MethodSymbol, MethodOrFunction> callables = new HashMap<>();

    private final TraitWithConstructorCompiler traitWithConstructorCompiler;

    public TypeDeclarationCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        index = JVerifyIndex.instance(compiler.context);
        reporter = Reporter.instance(compiler.context);
        methodOrLoopContractCompiler = MethodOrLoopContractCompiler.instance(compiler.context);
        jverifyUtils = JVerifyUtils.instance(compiler.context);
        verifyAnnotationCompiler = new VerifyAnnotationCompiler(compiler.context);
        types = Types.instance(compiler.context);
        nameCompiler = NameCompiler.instance(compiler.context);
        traitWithConstructorCompiler = new TraitWithConstructorCompiler(this);
    }

    List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree) {
        invariants.clear();
        if (tree instanceof JCTree.JCClassDecl classDecl) {

            if (classDecl.name.equals(classDecl.name.table.names.package_info)) {
                return List.of();
            }

            Name name = compiler.getName(classDecl, classDecl.sym);
            var origin = compiler.declToOrigin(classDecl, name);
            reporter.contextOrigins.push(origin);

            var classSymbol = classDecl.sym;
            @Nullable TopLevelDecl intermediateResult = switch (classDecl.getKind()) {
                case ENUM -> translateEnum(classDecl, origin, name);
                case INTERFACE, CLASS -> translateInterfaceOrClass(classDecl, origin, name);
                case RECORD -> new ImmutableTypeCompiler(this).translate(classDecl, origin, name);
                case ANNOTATION_TYPE -> {
                    compiler.reportError(classDecl, "notSupported", "%s declaration".formatted(classDecl.getKind()));
                    yield null;
                }
                // JCClassDecl#getKind returns one of the above five values
                default -> throw new JavaViolationException("unexpected kind: " + classDecl.getKind());
            };

            List<TopLevelDecl> result = new ArrayList<>();
            if (intermediateResult != null) {
                result.addAll(traitWithConstructorCompiler.compile(intermediateResult, classSymbol));
            }

            reporter.contextOrigins.pop();
            return result;
        }
        if (tree instanceof JCTree jcTree) {
            compiler.reportError(jcTree, "notSupported", "type declaration " + tree.getClass().getSimpleName());
            return List.of();
        } else {
            throw new JavaToDafnyCompiler.NotImplementedException(tree.getClass().getName());
        }
    }

    private IndDatatypeDecl translateEnum(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        List<DatatypeCtor> constructors = new ArrayList<>();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var variableName = compiler.nameCompiler.getCompiledName(variableDecl.sym, variableDecl);
                Name constructorName = compiler.getName(variableDecl, variableName);
                constructors.add(new DatatypeCtor(compiler.declToOrigin(variableDecl, constructorName), constructorName,
                        null, false, List.of()));

            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }

    private TopLevelDeclWithMembers translateInterfaceOrClass(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        if (compiler.isAnonymousOrFinalImmutableType(classDecl.sym) || compiler.isImmutableClass(classDecl.sym)) {
            return new ImmutableTypeCompiler(this).translate(classDecl, origin, name);
        }

        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (methodDecl.getModifiers().getAnnotations().stream().
                        anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident &&
                                ident.name.contentEquals("Invariant"))) {
                    invariants.add(methodDecl);
                }
            }
        }

        ArrayList<MemberDecl> members = new ArrayList<>();
        initializers.clear();
        // First translate all fields and store default initializers to add to constructors
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var dafnyMember = translateField(variableDecl);
                members.add(dafnyMember);
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

        List<JCTree.JCTypeParameter> javaTypeParams = classDecl.typarams;
        if (classDecl.sym.isDirectlyOrIndirectlyLocal()) {
            javaTypeParams = compiler.getOwnAndEnclosedTypeParameters(classDecl.sym).toList();
        }
        var typeParameters = translateTypeParameters(javaTypeParams);

        return getTraitDecl(origin, name, classDecl.sym, typeParameters, members);
    }

    public TraitDecl getTraitDecl(IOrigin origin, Name name,
                                  Symbol.ClassSymbol definingSymbol,
                                  List<TypeParameter> typeParameters,
                                  List<MemberDecl> members) {
        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null) {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }

        var symtab = Symtab.instance(this.compiler.context);
        var superTraits = baseTypes
                .filter(t -> t.tsym != null)
                .map((com.sun.tools.javac.code.Type type) -> {
                    if (type.baseType() == symtab.objectType) {
                        // A class that extends 'Object' will extend '@Modifiable Object' instead
                        return new UserDefinedType(origin, new NameSegment(origin, ModifiableObjectCompiler.REFERENCE_OBJECT_NAME, null));
                    }
                    return compiler.translateType(type, origin, null);
                })
                .collect(Collectors.<Type>toList());

        if (superTraits.isEmpty()) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null)));
        }

        var mutable = !JavaToDafnyCompiler.isInterface(definingSymbol)
                || compiler.isAnnotated(definingSymbol.type, Modifiable.class);
        if (mutable) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, DAFNY_REFERENCE_BASE_TYPE, null)));
        }

        return new TraitDecl(origin, new Name(name.getOrigin(), name.getValue()), null,
                typeParameters, members, superTraits, false);
    }

    private static TypeParameter getTypeParameter(IOrigin origin, 
                                                  com.sun.tools.javac.util.List<@Nullable Type> bounds, 
                                                  Name name) {
        boolean arrayParameter = name.getValue().equals("TArrayElement");
        boolean createArrayParameter = name.getValue().equals("TCreateArrayElement");
        if (bounds.isEmpty() && !arrayParameter && !createArrayParameter) {
            bounds = bounds.append(new UserDefinedType(origin,
                    new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null)));
        }
        return new TypeParameter(origin,
                name, null, TPVarianceSyntax.NonVariant_Strict,
                new TypeParameterCharacteristics(
                        TypeParameterEqualitySupportValue.Unspecified,
                        createArrayParameter ? TypeAutoInitInfo.Nonempty : TypeAutoInitInfo.MaybeEmpty,
                        false
                ),
                bounds);
    }

    public List<TypeParameter> translateTypeParameters(List<JCTree.JCTypeParameter> typarams) {
        return typarams.stream().map(p -> {
            var name = compiler.getName(p, p.getName());
            var bounds = p.bounds.map(compiler::translateType);

            IOrigin origin = compiler.toOrigin(p);
            return getTypeParameter(origin, bounds, name);
        }).toList();
    }

    public MemberDecl translateMember(JCTree member) {
        switch (member) {
            case JCTree.JCClassDecl _ -> {
                return null;
            }
            case JCTree.JCMethodDecl method -> {
                return translateMethodDecl(method);
            }
            case JCTree.JCVariableDecl variableDecl -> {
                return translateField(variableDecl);
            }
            case JCTree.JCBlock block -> {
                // LOWER generates empty static blocks
                if (block.stats.isEmpty()) {
                    return null;
                } else {
                    throw new JavaToDafnyCompiler.NotImplementedException(member.getClass().getName());
                }
            }
            default -> throw new JavaToDafnyCompiler.NotImplementedException(member.getClass().getName());
        }
    }

    public Field translateField(JCTree.JCVariableDecl variableDecl) {
        var varFlags = variableDecl.getModifiers().getFlags();
        Name fieldName = compiler.getName(variableDecl, variableDecl.sym);
        IOrigin origin = compiler.declToOrigin(variableDecl, fieldName);
        Type type = compiler.translateType(variableDecl.type, 
                compiler.toOrigin(variableDecl.vartype), variableDecl.getModifiers()
        );
        
        if (variableDecl.getInitializer() != null) {
            if (varFlags.contains(Modifier.FINAL)) {
                var rhs = compiler.expressionCompiler.toExpr(variableDecl.getInitializer());
                var isStatic = varFlags.contains(Modifier.STATIC);
                return new ConstantField(origin, fieldName, null, JavaToDafnyCompiler.Ghostness, type, rhs, isStatic, false);
            }

            // Keep this variable declaration in the initializers list to be added to constructors laters
            initializers.add(variableDecl);
        }
        return new Field(origin, fieldName, null, JavaToDafnyCompiler.Ghostness, type);
    }


    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        var methodSymbol = method.sym;
        compiler.symbolsWithAContract.add(methodSymbol);
        var annotationsByName = JavaToDafnyCompiler.getAnnotationsByName(method.mods);
        if (method.body == null) {
            return null;
        }
        boolean shouldVerify = MethodOrLoopContractCompiler.hasImplementation(method);
        
        if (annotationsByName.containsKey(InheritContract.class.getName())) {
// Hints for whenever this is implemented.
//            var types = Types.instance(context);
//            var container = method.sym.enclClass();
//            var impl = method.sym.implemented(container, types);
            compiler.reportError(method, "notSupported", "@InheritContract");
            return null;
        }

        var contract = new MethodOrLoopContract(method, jverifyUtils.isPure(method.sym));
        var remainingStatements = methodOrLoopContractCompiler.
                extractContract(compiler, method.body, contract);

        if (contract.isPure) {
            return translatePureMethod(method, shouldVerify, contract);
        } else {
            return translateImpureMethod(method, shouldVerify, contract, remainingStatements);
        }
    }

    private MethodOrConstructor translateImpureMethod(JCTree.JCMethodDecl method,
                                                      boolean shouldVerify,
                                                      MethodOrLoopContract contract,
                                                      com.sun.tools.javac.util.List<JCTree.JCStatement> postHeader) {
        var methodOrigin = compiler.toOrigin(method);

        var dafnyTypeParameters = translateTypeParameters(method.getTypeParameters());

        var blockCompiler = new BlockCompiler(compiler, method.sym);
        var name = compiler.getName(method, method.sym);
        var origin = compiler.declToOrigin(method, name);
        var isStatic = JavaToDafnyCompiler.isStatic(method.mods);
        List<Formal> ins = getIns(method, shouldVerify, methodOrigin);

        applyInvariants(method.mods, method.sym, contract);
        blockCompiler.checkEmptyExpressions(method, contract.invariants, "invariants", "method");

        var outs = new ArrayList<Formal>();
        if (method.sym.type.getReturnType() != null) {
            JVerifyIndex index = JVerifyIndex.instance(compiler.context);
            var methodDecl = (JCTree.JCMethodDecl) index.getTree(method.sym);
            IOrigin returnOrigin;
            if (methodDecl == null) {
                returnOrigin = methodOrigin;
            } else {
                var returnTypeDecl = methodDecl.getReturnType();
                returnOrigin = compiler.toOrigin(returnTypeDecl);
            }
            var returnType = compiler.translateMethodSignatureType(method.sym.type.getReturnType(), returnOrigin, shouldVerify);
            if (returnType != null) {
                outs.add(makeReturnFormal(origin, returnType));
            }
        }

        if (JavaToDafnyCompiler.isConstructor(method.sym)) {
            DividedBlockStmt body;
            if (shouldVerify) {
                var treeMaker = TreeMaker.instance(compiler.context);

                var bodyStatements = new ArrayList<Statement>();
                if (!postHeader.isEmpty()) {
                    var first = postHeader.getFirst();
                    if (first instanceof JCTree.JCExpressionStatement expressionStatement 
                            && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation methodInvocation && BlockCompiler.getSuperIdent(methodInvocation) != null) {
                        bodyStatements.addAll(blockCompiler.translateStatement(first));
                        postHeader = postHeader.tail;
                    }
                }
                for (JCTree.JCVariableDecl variableDecl : initializers) {
                    var rhs = variableDecl.getInitializer();
                    var assignStmt = treeMaker.Assignment(variableDecl.sym, rhs);
                    bodyStatements.addAll(blockCompiler.translateStatement(assignStmt, methodOrigin));
                }
                bodyStatements.addAll(blockCompiler.translateStatements(postHeader));

                body = new DividedBlockStmt(methodOrigin, null, List.of(), bodyStatements, null, List.of());
            } else {
                body = null;
            }

            return new Constructor(origin, name, null, JavaToDafnyCompiler.Ghostness, 
                    null, dafnyTypeParameters, ins,
                    contract.preconditions, contract.postconditions, contract.getReads(),
                    contract.getDecreases(), contract.getModifies(),
                    body);
        } else {
            List<Statement> bodyStatements;
            if (shouldVerify) {
                bodyStatements = blockCompiler.translateStatements(postHeader);
            } else {
                // Leaving out the body does not assume the body for traits
                // So we need to have a body with an assume statement
                // Would be nicer (and faster) if Dafny had explicit assumed bodies
                bodyStatements = List.of(new AssumeStmt(origin, null, new LiteralExpr(origin, false)));
            }
            var body = new BlockStmt(methodOrigin, null, List.of(), bodyStatements);
            var result = new Method(origin, name, null, JavaToDafnyCompiler.Ghostness, null, dafnyTypeParameters,
                    ins, contract.preconditions, contract.postconditions, contract.getReads(),
                    contract.getDecreases(), contract.getModifies(),
                    isStatic, outs,
                    body, false);
            callables.put(method.sym, result);
            return result;
        }
    }

    private Function translatePureMethod(JCTree.JCMethodDecl method, 
                                         boolean shouldVerify,
                                         MethodOrLoopContract contract) {
        var sourceOrigin = compiler.toOrigin(method);

        var name = compiler.getName(method, method.sym);
        var origin = compiler.declToOrigin(method, name);
        var isStatic = JavaToDafnyCompiler.isStatic(method.mods);
        List<Formal> ins = getIns(method, shouldVerify, sourceOrigin);
        var returnType = compiler.translateMethodSignatureType(method.sym.type.getReturnType(), sourceOrigin, shouldVerify);
        if (returnType == null) {
            compiler.reportError(method, "pureMethodsNeedsReturnType");
            return null;
        }
        applyInvariants(method.mods, method.sym, contract);
        var dafnyTypeParameters = translateTypeParameters(method.getTypeParameters());

        Function result = new Function(origin, name, null, JavaToDafnyCompiler.Ghostness, 
                null, dafnyTypeParameters,
                ins, contract.preconditions, contract.postconditions, contract.getReads(),
                contract.getDecreases(), isStatic, false, makeReturnFormal(origin, returnType),
                returnType, contract.pureBody, null, null);
        callables.put(method.sym, result);
        return result;
    }

    private void applyInvariants(JCTree.JCModifiers modifiers, Symbol.MethodSymbol methodSymbol, MethodOrLoopContract header) {
        boolean isPublic = (modifiers.flags & Flags.PUBLIC) != 0;
        boolean isStaticMethod = JavaToDafnyCompiler.isStatic(modifiers);

        // Only apply invariants to public instance methods (not static methods)
        if (isPublic && !isStaticMethod) {
            for (var invariant : invariants) {
                var memberName = compiler.nameCompiler.getCompiledName(invariant.sym, invariant);
                var invariantName = compiler.getName(invariant, invariant.getName());
                var invariantOrigin = compiler.declToOrigin(invariant, invariantName);
                ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                        memberName, null), null, new ActualBindings(List.of()), null);
                var invariantCall = new AttributedExpression(call, null, null);
                if (!JavaToDafnyCompiler.isConstructor(methodSymbol)) {
                    header.preconditions.add(invariantCall);
                }
                header.postconditions.add(invariantCall);
            }
        }
    }

    private List<Formal> getIns(JCTree.JCMethodDecl method, boolean shouldVerify, IOrigin bodyOrigin) {
        var methodSymbol = method.sym;
        var parameterSymbols = methodSymbol.extraParams.
                appendList(methodSymbol.getParameters()).
                appendList(methodSymbol.capturedLocals);
        return IntStream.range(0, method.getParameters().size()).mapToObj(index -> {
            var parameter = method.getParameters().get(index);
            var parameterSymbol = parameterSymbols.get(index);
            IOrigin parameterOrigin = compiler.toOrigin(parameter);
            Name formalName = new Name(parameterOrigin, compiler.nameCompiler.getCompiledName(parameter.sym, parameter));
            // TODO use parameter.sym.type ?
            var syntacticType = compiler.translateMethodSignatureType(parameterSymbol.type, parameterOrigin, shouldVerify);
            return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        }).toList();
    }

    private Formal makeReturnFormal(IOrigin origin, Type syntacticType) {
        var name = new Name(origin, NameCompiler.RETURN_VARIABLE_NAME);
        return new Formal(origin, name, syntacticType, false, false, null, null, false, false, false, null);
    }


    private final Map<Symbol.TypeSymbol, Set<MethodOrFunction>> inheritedUnverifiedMethodsForTypes = new HashMap<>();

    private final Map<Symbol.TypeSymbol, Set<String>> definedMethodsCache = new HashMap<>();
    public Set<String> getBodiedMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin) {
        var result = definedMethodsCache.get(typeSymbol);
        if (result != null) {
            return result;
        }
        
        result = new HashSet<>();

        var decl = (JCTree.JCClassDecl)index.getTree(typeSymbol);
        for(var member : decl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl method) {
                var methodSymbol = method.sym;
                if (MethodOrLoopContractCompiler.hasImplementation(method)) {
                    result.add(nameCompiler.getCompiledName(methodSymbol, origin));
                }
            }
        }

        for(var baseType : types.interfaces(typeSymbol.type).append(types.supertype(typeSymbol.type))) {
            if (baseType == com.sun.tools.javac.code.Type.noType) {
                continue;
            }
            result.addAll(getBodiedMethods(baseType.tsym, origin));
        }
        
        definedMethodsCache.put(typeSymbol, result);
        return result;
    }
    
    public Set<MethodOrFunction> getBodylessMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin) {
        var result = inheritedUnverifiedMethodsForTypes.get(typeSymbol);
        if (result == null) {
            result = getBodylessMethods(typeSymbol, origin, true);
            inheritedUnverifiedMethodsForTypes.put(typeSymbol, result);
        }
        return result;
    }
    
    public Set<MethodOrFunction> getBodylessMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin, boolean includeSelf) {
        var result = new HashSet<MethodOrFunction>();
        var names = getBodiedMethods(typeSymbol, origin);

        var decl = (JCTree.JCClassDecl)index.getTree(typeSymbol);
        if (includeSelf) {
            for(var member : decl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl method) {
                    var methodSymbol = method.sym;
                    if (verifyAnnotationCompiler.removedImplementations.contains(methodSymbol)) {
                        MethodOrFunction callable = callables.get(methodSymbol);
                        if (callable != null) {
                            if (callable instanceof Method dafnyMethod && dafnyMethod.getBody() == null ||
                                            callable instanceof Function dafnyFunction && dafnyFunction.getBody() == null) {
                                result.add(callable);
                            } else {
                                names.add(nameCompiler.getCompiledName(methodSymbol, origin));
                            }
                        }
                    }
                }
            }
        }
        
        for(var baseType : types.interfaces(typeSymbol.type).append(types.supertype(typeSymbol.type))) {
            if (baseType.tsym != null && baseType.tsym != typeSymbol) {
                for(var unverified : getBodylessMethods(baseType.tsym, origin)) {
                    if (!names.contains(unverified.getNameNode().getValue())) {
                        result.add(unverified);
                    }
                }
            }
        }
        return result;
    }
}
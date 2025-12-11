package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.*;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.dafnygenerator.DafnyGenerator;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopDafnyContract;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.ImpureObjectGenerator;
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
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TypeDeclarationCompiler {
    private static final String DAFNY_REFERENCE_BASE_TYPE = "object";
    public final DafnyGenerator generator;
    public final BaseDafnyGenerator baseGenerator;
    private final Types types;
    private final NameCompiler nameCompiler;
    private final ExpressionCompiler expressionCompiler;
    private final MethodOrLoopContractCompiler methodOrLoopContractCompiler;
    private final Reporter reporter;
    private final JVerifyIndex index;
    private final PureTypeCompiler pureTypeCompiler;
    private final Symtab symtab;
    private final TreeMaker treeMaker;
    private final JVerifyUtils jverifyUtils;
    private final List<JCTree.JCMethodDecl> typeInvariants = new ArrayList<>();
    private final List<JCTree.JCVariableDecl> initializers = new ArrayList<>();
    public final Map<Symbol.MethodSymbol, MethodOrFunction> callables = new HashMap<>();

    private final TraitWithConstructorCompiler traitWithConstructorCompiler;

    public TypeDeclarationCompiler(Context context) {
        this.generator = context.get(DafnyGenerator.class);
        baseGenerator = context.get(BaseDafnyGenerator.class);
        index = JVerifyIndex.instance(context);
        reporter = Reporter.instance(context);
        treeMaker = TreeMaker.instance(context);
        symtab = Symtab.instance(context);
        methodOrLoopContractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        expressionCompiler = baseGenerator.expressionCompiler;
        types = Types.instance(context);
        nameCompiler = NameCompiler.instance(context);
        traitWithConstructorCompiler = new TraitWithConstructorCompiler(context, this);
        pureTypeCompiler = new PureTypeCompiler(context, this);
    }

    List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree) {
        typeInvariants.clear();
        if (tree instanceof JCTree.JCClassDecl classDecl) {

            if (classDecl.name.equals(classDecl.name.table.names.package_info)) {
                return List.of();
            }

            Name name = nameCompiler.getName(classDecl, classDecl.sym);
            var origin = reporter.declToOrigin(classDecl, name);
            reporter.contextOrigins.push(origin);

            var classSymbol = classDecl.sym;
            @Nullable TopLevelDecl intermediateResult = switch (classDecl.getKind()) {
                case ENUM -> translateEnum(classDecl, origin, name);
                case INTERFACE, CLASS -> translateInterfaceOrClass(classDecl, origin, name);
                case RECORD -> pureTypeCompiler.translate(classDecl, origin, name);
                case ANNOTATION_TYPE -> {
                    reporter.reportError(classDecl, "notSupported", "%s declaration".formatted(classDecl.getKind()));
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
            reporter.reportError(jcTree, "notSupported", "type declaration " + tree.getClass().getSimpleName());
            return List.of();
        } else {
            throw new RuntimeException(tree.getClass().getName());
        }
    }

    private IndDatatypeDecl translateEnum(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        List<DatatypeCtor> constructors = new ArrayList<>();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var variableName = nameCompiler.getCompiledName(variableDecl.sym, variableDecl);
                Name constructorName = reporter.getName(variableDecl, variableName);
                constructors.add(new DatatypeCtor(reporter.declToOrigin(variableDecl, constructorName), constructorName,
                        null, false, List.of()));
            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }

    private TopLevelDeclWithMembers translateInterfaceOrClass(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        if (baseGenerator.isAnonymousOrFinalImmutableType(classDecl.sym) || baseGenerator.isImmutableClass(classDecl.sym)) {
            return pureTypeCompiler.translate(classDecl, origin, name);
        }

        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (methodDecl.getModifiers().getAnnotations().stream().
                        anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident &&
                                ident.name.contentEquals("Invariant"))) {
                    typeInvariants.add(methodDecl);
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
            javaTypeParams = baseGenerator.getOwnAndEnclosedTypeParameters(classDecl.sym).toList();
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

        var superTraits = baseTypes
                .filter(t -> t.tsym != null)
                .map((com.sun.tools.javac.code.Type type) -> {
                    if (type.baseType() == symtab.objectType) {
                        // A class that extends 'Object' will extend '@Impure Object' instead
                        return new UserDefinedType(origin, new NameSegment(origin, ImpureObjectGenerator.IMPURE_OBJECT_NAME, null));
                    }
                    return generator.translateType(type, origin, null);
                })
                .collect(Collectors.<Type>toList());

        if (superTraits.isEmpty()) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, BaseDafnyGenerator.PURE_OBJECT_NAME, null)));
        }

        var mutable = !JVerifyUtils.isInterface(definingSymbol)
                || JVerifyUtils.isAnnotated(definingSymbol.type, Impure.class);
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
                    new NameSegment(origin, BaseDafnyGenerator.PURE_OBJECT_NAME, null)));
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
            var name = reporter.getName(p, p.getName());
            var bounds = p.bounds.map(baseGenerator::translateType);

            IOrigin origin = reporter.toOrigin(p);
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
                    reporter.reportError(block, "notSupported", "an initializer block");
                    return null;
                }
            }
            default -> throw new RuntimeException(member.getClass().getName());
        }
    }

    public Field translateField(JCTree.JCVariableDecl variableDecl) {
        var varFlags = variableDecl.getModifiers().getFlags();
        Name fieldName = nameCompiler.getName(variableDecl, variableDecl.sym);
        IOrigin origin = reporter.declToOrigin(variableDecl, fieldName);
        Type type = generator.translateType(variableDecl.type,
                reporter.toOrigin(variableDecl.vartype), variableDecl.getModifiers()
        );

//        if (varFlags.contains(Modifier.FINAL)) {
//            Expression rhs = null;
//            if (variableDecl.getInitializer() != null) {
//                rhs = compiler.expressionCompiler.toExpr(variableDecl.getInitializer(), ExpressionContext.Pure);
//            }
//            var isStatic = varFlags.contains(Modifier.STATIC);
//            return new ConstantField(origin, fieldName, null, BaseDafnyGenerator.Ghostness, type, rhs, isStatic, false);
//        }
        
        if (variableDecl.getInitializer() != null) {
            // This block should be removed when the above block is commented in.
            if (varFlags.contains(Modifier.FINAL)) {
                var rhs = expressionCompiler.toExprWithFlows(variableDecl.getInitializer(), ExpressionContext.Pure).expression();
                var isStatic = varFlags.contains(Modifier.STATIC);
                return new ConstantField(origin, fieldName, null, BaseDafnyGenerator.Ghostness, type, rhs, isStatic, false);
            }
            
            // Keep this variable declaration in the initializers list to be added to constructors laters
            initializers.add(variableDecl);
        }
        return new Field(origin, fieldName, null, BaseDafnyGenerator.Ghostness, type);
    }


    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        var methodSymbol = method.sym;
        baseGenerator.symbolsWithAContract.add(methodSymbol);
        var annotationsByName = JVerifyUtils.getAnnotationsByName(method.mods);
        if (method.body == null) {
            return null;
        }
        boolean shouldVerify = MethodOrLoopContractCompiler.hasImplementation(method);
        
        if (annotationsByName.containsKey(InheritContract.class.getName())) {
// Hints for whenever this is implemented.
//            var types = Types.instance(context);
//            var container = method.sym.enclClass();
//            var impl = method.sym.implemented(container, types);
            reporter.reportError(method, "notSupported", "@InheritContract");
            return null;
        }

        var contract = new MethodOrLoopDafnyContract(method, jverifyUtils.isPure(method.sym));
        baseGenerator.fillDafnyContract(method.body, contract);

        if (contract.isPure) {
            return translatePureMethod(method, shouldVerify, contract);
        } else {
            return translateImpureMethod(method, shouldVerify, contract, MethodOrLoopContractCompiler.getImplementationStatements(method.body));
        }
    }

    private MethodOrConstructor translateImpureMethod(JCTree.JCMethodDecl method,
                                                      boolean shouldVerify,
                                                      MethodOrLoopDafnyContract contract,
                                                      com.sun.tools.javac.util.List<JCTree.JCStatement> postHeader) {
        var methodOrigin = reporter.toOrigin(method);

        var dafnyTypeParameters = translateTypeParameters(method.getTypeParameters());

        var blockCompiler = new BlockCompiler(baseGenerator, method.sym);
        var name = nameCompiler.getName(method, method.sym);
        var origin = reporter.declToOrigin(method, name);
        var isStatic = JVerifyUtils.isStatic(method.mods);
        List<Formal> ins = getIns(method, shouldVerify, methodOrigin);

        applyInvariants(method.mods, method.sym, contract);

        var outs = new ArrayList<Formal>();
        if (method.sym.type.getReturnType() != null) {
            var methodDecl = (JCTree.JCMethodDecl) index.getTree(method.sym);
            IOrigin returnOrigin;
            if (methodDecl == null) {
                returnOrigin = methodOrigin;
            } else {
                var returnTypeDecl = methodDecl.getReturnType();
                returnOrigin = reporter.toOrigin(returnTypeDecl);
            }
            var returnType = baseGenerator.translateMethodSignatureType(method.sym.type.getReturnType(), returnOrigin, shouldVerify);
            if (returnType != null) {
                outs.add(makeReturnFormal(origin, returnType));
            }
        }

        if (JVerifyUtils.isConstructor(method.sym)) {
            DividedBlockStmt body;
            if (shouldVerify) {

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

            return new Constructor(origin, name, null, BaseDafnyGenerator.Ghostness, 
                    null, dafnyTypeParameters, ins,
                    contract.preconditions, contract.postconditions, contract.getReads(),
                    contract.getDecreases(), contract.getModifies(),
                    body);
        } else {
            BlockStmt body = null;
            if (shouldVerify) {
                 body = new BlockStmt(methodOrigin, null, List.of(), blockCompiler.translateStatements(postHeader));
            }
            var result = new Method(origin, name, null, BaseDafnyGenerator.Ghostness, null, dafnyTypeParameters,
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
                                         MethodOrLoopDafnyContract contract) {
        var sourceOrigin = reporter.toOrigin(method);

        var name = nameCompiler.getName(method, method.sym);
        var origin = reporter.declToOrigin(method, name);
        var isStatic = JVerifyUtils.isStatic(method.mods);
        List<Formal> ins = getIns(method, shouldVerify, sourceOrigin);
        var returnType = baseGenerator.translateMethodSignatureType(method.sym.type.getReturnType(), sourceOrigin, shouldVerify);
        if (returnType == null) {
            reporter.reportError(method, "pureMethodsNeedsReturnType");
            return null;
        }
        applyInvariants(method.mods, method.sym, contract);
        var dafnyTypeParameters = translateTypeParameters(method.getTypeParameters());

        Function result = new Function(origin, name, null, BaseDafnyGenerator.Ghostness, 
                null, dafnyTypeParameters,
                ins, contract.preconditions, contract.postconditions, contract.getReads(),
                contract.getDecreases(), isStatic, false, makeReturnFormal(origin, returnType),
                returnType, contract.pureBody, null, null);
        callables.put(method.sym, result);
        return result;
    }

    private void applyInvariants(JCTree.JCModifiers modifiers, Symbol.MethodSymbol methodSymbol, MethodOrLoopDafnyContract header) {
        boolean isPublic = (modifiers.flags & Flags.PUBLIC) != 0;
        boolean isStaticMethod = JVerifyUtils.isStatic(modifiers);

        // Only apply invariants to public instance methods (not static methods)
        if (isPublic && !isStaticMethod && methodSymbol.getAnnotation(Invariant.class) == null) {
            for (var invariant : typeInvariants) {
                var memberName = nameCompiler.getCompiledName(invariant.sym, invariant);
                var invariantName = reporter.getName(invariant, invariant.getName());
                var invariantOrigin = reporter.declToOrigin(invariant, invariantName);
                ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                        memberName, null), null, new ActualBindings(List.of()), null);
                var invariantCall = new AttributedExpression(call, null, null);
                if (!JVerifyUtils.isConstructor(methodSymbol)) {
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
            IOrigin parameterOrigin = reporter.toOrigin(parameter);
            Name formalName = new Name(parameterOrigin, nameCompiler.getCompiledName(parameter.sym, parameter));
            // TODO use parameter.sym.type ?
            var syntacticType = baseGenerator.translateMethodSignatureType(parameterSymbol.type, parameterOrigin, shouldVerify);
            return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        }).toList();
    }

    private Formal makeReturnFormal(IOrigin origin, Type syntacticType) {
        var name = new Name(origin, NameCompiler.RETURN_VARIABLE_NAME);
        return new Formal(origin, name, syntacticType, false, false, null, null, false, false, false, null);
    }


    private final Map<Symbol.TypeSymbol, Map<String, MethodOrFunction>> inheritedUnverifiedMethodsForTypes = new HashMap<>();

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
    
    public Map<String, MethodOrFunction> getBodylessMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin) {
        var result = inheritedUnverifiedMethodsForTypes.get(typeSymbol);
        if (result == null) {
            result = getBodylessMethods(typeSymbol, origin, true);
            inheritedUnverifiedMethodsForTypes.put(typeSymbol, result);
        }
        return result;
    }
    
    public Map<String, MethodOrFunction> getBodylessMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin, boolean includeSelf) {
        var result = new HashMap<String, MethodOrFunction>();
        var names = getBodiedMethods(typeSymbol, origin);

        var decl = (JCTree.JCClassDecl)index.getTree(typeSymbol);
        for(var member : decl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl method) {
                var methodSymbol = method.sym;
                MethodOrFunction callable = callables.get(methodSymbol);
                if (callable != null) {
                    boolean bodiless = callable instanceof Method dafnyMethod && dafnyMethod.getBody() == null ||
                            callable instanceof Function dafnyFunction && dafnyFunction.getBody() == null;
                    String compiledName = nameCompiler.getCompiledName(methodSymbol, origin);
                    if (includeSelf && bodiless) {
                        result.putIfAbsent(compiledName, callable);
                    } else {
                        names.add(compiledName);
                    }
                }
            }
        }
        
        for(var baseType : types.interfaces(typeSymbol.type).append(types.supertype(typeSymbol.type))) {
            if (baseType.tsym != null && baseType.tsym != typeSymbol) {
                for(var entry : getBodylessMethods(baseType.tsym, origin).entrySet()) {
                    if (!names.contains(entry.getKey())) {
                        result.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return result;
    }
}
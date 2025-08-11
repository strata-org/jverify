package com.aws.jverify.verifier.compiler;

import com.aws.jverify.*;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.simplifications.ModifiableObjectCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.aws.jverify.verifier.compiler.simplifications.workaround.TraitWithConstructorCompiler;
import com.aws.jverify.verifier.compiler.simplifications.ImmutableTypeCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeDeclarationCompiler {
    private static final String DAFNY_REFERENCE_BASE_TYPE = "object";
    public final JavaToDafnyCompiler compiler;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;
    private final List<JCTree.JCMethodDecl> invariants = new ArrayList<>();
    private final List<JCTree.JCVariableDecl> initializers = new ArrayList<>();

    private final TraitWithConstructorCompiler traitWithConstructorCompiler = new TraitWithConstructorCompiler(this);

    public TypeDeclarationCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {

            if (classDecl.name.equals(classDecl.name.table.names.package_info)) {
                return List.of();
            }
            var annotationsByName = JavaToDafnyCompiler.getAnnotationsByName(classDecl.getModifiers());

            Name name = null;
            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation == null) {
                for (var member : classDecl.getMembers()) {
                    if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                        continue;
                    }
                    // Don't report errors when extracting this contract here,
                    // since the actual translation of the method will report them.
                    var header = new BlockCompiler(compiler, methodDecl.sym).extractContract(methodDecl, false);
                }
            } else {
                var contractee = compiler.externalContractCompiler.getContractTarget(classDecl, contractAnnotation);
                if (contractee != null) {

                    if (compiler.typeHasSource(contractee)) {
                        // If the contractee has source, then we have a merged contract
                        // And we do not need to traverse the contracter.
                        // The contractee will lookup contracts in the contractor
                        // for bodyless members
                        var modifiableAnnotation = annotationsByName.get(Modifiable.class.getName());
                        if (modifiableAnnotation != null) {
                            compiler.reportError(modifiableAnnotation, "annotationOnSourceContractClass", Modifiable.class.getSimpleName(), classDecl.name.toString());
                        }
                        return List.of();
                    }


                    typeForWhichCurrentClassIsDefiningContract = contractee;
                    name = compiler.getName(classDecl, typeForWhichCurrentClassIsDefiningContract);
                }
            }

            if (name == null) {
                name = compiler.getName(classDecl, classDecl.sym);
            }
            var origin = compiler.declToOrigin(classDecl, name);
            compiler.contextOrigins.push(origin);

            var mode = compiler.verifyAnnotationCompiler.getShouldVerifyMode(annotationsByName);
            if (typeForWhichCurrentClassIsDefiningContract != null) {
                mode = VerifyAnnotationCompiler.ShouldVerifyMode.AlwaysNo;
            }
            compiler.verifyAnnotationCompiler.addShouldVerify(mode);

            var classSymbol = getCurrentTypeSymbol(classDecl.sym);
            @Nullable TopLevelDecl intermediateResult = switch (classDecl.getKind()) {
                case ENUM -> translateEnum(classDecl, origin, name);
                case INTERFACE, CLASS -> translateInterfaceOrClass(classDecl, origin, name);
                case RECORD -> new ImmutableTypeCompiler(this).translate(classSymbol, classDecl, origin, name);
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

            typeForWhichCurrentClassIsDefiningContract = null;
            compiler.contextOrigins.pop();
            compiler.verifyAnnotationCompiler.shouldVerifies.pop();
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
                var variableName = compiler.nameCompiler.getCompiledName(variableDecl.sym);
                Name constructorName = compiler.getName(variableDecl, variableName);
                constructors.add(new DatatypeCtor(compiler.declToOrigin(variableDecl, constructorName), constructorName,
                        null, false, List.of()));

            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }

    private TopLevelDeclWithMembers translateInterfaceOrClass(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        var contractAnnotation = classDecl.sym.getAnnotation(Contract.class);
        if (compiler.isAnonymousOrFinalImmutableType(classDecl.sym)) {
            return new ImmutableTypeCompiler(this).translate(classDecl.sym, classDecl, origin, name);
        }
        
        if (contractAnnotation != null && contractAnnotation.immutable()) {
            if (typeForWhichCurrentClassIsDefiningContract != null) {
                return new ImmutableTypeCompiler(this).translate(typeForWhichCurrentClassIsDefiningContract, classDecl, origin, name);
            } else {
                compiler.reportError(classDecl, "immutableInternalContract");
                return null;
            }
        }

        invariants.clear();

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

        var externalContract = compiler.externalContractCompiler.externalContracts.get(classDecl.sym);
        if (externalContract != null) {
            for (var ghostField : externalContract.ghostFields()) {
                var dafnyMember = translateField(ghostField);
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

        var definingSymbol = getCurrentTypeSymbol(classDecl.sym);

        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null) {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }

        var symtab = Symtab.instance(this.compiler.context);
        var superTraits = baseTypes
                .filter(compiler::typeHasAContract)
                .map((com.sun.tools.javac.code.Type type) -> {
                    if (type.baseType() == symtab.objectType) {
                        // A class that extends 'Object' will extend '@Modifiable Object' instead
                        return new UserDefinedType(origin, new NameSegment(origin, ModifiableObjectCompiler.REFERENCE_OBJECT_NAME, null));
                    }
                    return compiler.translateType(type, origin, null);
                })
                .collect(Collectors.<Type>toList());

        if (definingSymbol == symtab.objectType.tsym || definingSymbol == symtab.recordType.tsym) {
            superTraits = new ArrayList<>();
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null)));
        }

        var mutable = !JavaToDafnyCompiler.isInterface(definingSymbol)
                || compiler.isAnnotated(definingSymbol.type, Modifiable.class);
        if (mutable) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, DAFNY_REFERENCE_BASE_TYPE, null)));
        }

        List<JCTree.JCTypeParameter> javaTypeParams = classDecl.typarams;
        if (classDecl.sym.isDirectlyOrIndirectlyLocal()) {
            javaTypeParams = compiler.getAllOwnerTypeParameters(classDecl.sym).toList();
        }
        var typeParameters = translateTypeParameters(javaTypeParams);
        return new TraitDecl(origin, new Name(name.getOrigin(), name.getValue()), null,
                typeParameters, members, superTraits, false);
    }

    public List<TypeParameter> translateTypeParameters(List<JCTree.JCTypeParameter> typarams) {
        return typarams.stream().map(p -> {
            var name = compiler.getName(p, p.getName());
            var bounds = p.bounds.map(compiler::translateType);

            IOrigin origin = compiler.toOrigin(p);
            bounds = bounds.append(new UserDefinedType(origin,
                        new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null)));
            return new TypeParameter(origin,
                    name, null, TPVarianceSyntax.NonVariant_Strict,
                    new TypeParameterCharacteristics(
                            TypeParameterEqualitySupportValue.Unspecified,
                            TypeAutoInitInfo.MaybeEmpty,
                            false
                    ),
                    bounds);
        }).toList();
    }

    public Symbol.ClassSymbol getCurrentTypeSymbol(Symbol.ClassSymbol classSymbol) {
        return typeForWhichCurrentClassIsDefiningContract == null ? classSymbol : typeForWhichCurrentClassIsDefiningContract;
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
                return new ConstantField(origin, fieldName, null, false, type, rhs, isStatic, false);
            }

            // Keep this variable declaration in the initializers list to be added to constructors laters
            initializers.add(variableDecl);
        }
        return new Field(origin, fieldName, null, false, type);
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
        if (typeForWhichCurrentClassIsDefiningContract != null && compiler.isSynthetic(source, methodSymbol)) {
            return null;
        }
        compiler.symbolsWithAContract.add(methodSymbol);

        var annotations = modifiers.getAnnotations();
        var annotationsByName = JavaToDafnyCompiler.getAnnotationsByName(modifiers);

        boolean shouldVerify = compiler.verifyAnnotationCompiler.processVerifyAnnotationAndPop(annotationsByName);

        if (annotationsByName.containsKey(InheritContract.class.getName())) {
// Hints for whenever this is implemented.
//            var types = Types.instance(context);
//            var container = method.sym.enclClass();
//            var impl = method.sym.implemented(container, types);
            compiler.reportError(source, "notSupported", "@InheritContract");
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
        var bodyOrigin = compiler.toOrigin(sourceBody);

        var dafnyTypeParameters = translateTypeParameters(typeParameters);

        var blockCompiler = new BlockCompiler(compiler, methodSymbol);
        var name = compiler.getName(source, methodSymbol);
        var origin = compiler.declToOrigin(source, name);
        var isStatic = JavaToDafnyCompiler.isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol, shouldVerify, bodyOrigin);

        MethodOrLoopContract header;
        List<Statement> bodyStatements = null;
        if (sourceBody instanceof JCTree.JCExpression expressionBody) {
            if (shouldVerify) {
                bodyStatements = List.of(
                        new ReturnStmt(bodyOrigin, null, List.of(
                                new ExprRhs(bodyOrigin, null, compiler.expressionCompiler.toExpr(expressionBody)))));
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
                if (externalContract != null && !JavaToDafnyCompiler.isConstructor(methodSymbol)) {
                    compiler.reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                if (contractOverride != null) {
                    header = contractOverride;
                } else {
                    header = new MethodOrLoopContract(source, false);
                }
                var allowFooter = JavaToDafnyCompiler.isConstructor(methodSymbol);
                List<JCTree.JCStatement> postHeader = new MethodOrLoopContractCompiler(compiler).
                        translateHeader(((JCTree.JCBlock) sourceBody).stats, header, allowFooter, true);
                if (shouldVerify) {
                    bodyStatements = blockCompiler.translateStatements(postHeader);
                }
            }
        }
        applyInvariants(sourceBody, modifiers, methodSymbol, header);
        blockCompiler.checkEmptyExpressions(source, header.invariants, "invariants", "method");

        var outs = new ArrayList<Formal>();
        if (methodSymbol.type.getReturnType() != null) {
            JVerifyIndex index = JVerifyIndex.instance(compiler.context);
            var methodDecl = (JCTree.JCMethodDecl) index.getTree(methodSymbol);
            IOrigin returnOrigin;
            if (methodDecl == null) {
                returnOrigin = bodyOrigin;
            } else {
                var returnTypeDecl = methodDecl.getReturnType();
                returnOrigin = compiler.toOrigin(returnTypeDecl);
            }
            var returnType = compiler.translateMethodSignatureType(methodSymbol.type.getReturnType(), returnOrigin, shouldVerify);
            if (returnType != null) {
                outs.add(makeReturnFormal(origin, returnType));
            }
        }

        if (JavaToDafnyCompiler.isConstructor(methodSymbol)) {
            DividedBlockStmt body;
            if (shouldVerify) {
                var treeMaker = TreeMaker.instance(compiler.context);

                var newBodyStatements = new ArrayList<Statement>();
                for (JCTree.JCVariableDecl variableDecl : initializers) {
                    var rhs = variableDecl.getInitializer();
                    var assignStmt = treeMaker.Assignment(variableDecl.sym, rhs);
                    newBodyStatements.addAll(blockCompiler.translateStatement(assignStmt, bodyOrigin));
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

    private Function translatePureMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                 Symbol.MethodSymbol methodSymbol, JCTree sourceBody,
                                                 List<JCTree.JCTypeParameter> typeParameters, boolean shouldVerify,
                                                 @Nullable MethodOrLoopContract contractOverride) {
        var bodyOrigin = compiler.toOrigin(sourceBody);

        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var name = compiler.getName(source, methodSymbol);
        var origin = compiler.declToOrigin(source, name);
        var isStatic = JavaToDafnyCompiler.isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol, shouldVerify, bodyOrigin);
        Expression body = null;
        MethodOrLoopContract header;
        var returnType = compiler.translateMethodSignatureType(methodSymbol.type.getReturnType(), bodyOrigin, shouldVerify);
        if (returnType == null) {
            compiler.reportError(source, "pureMethodsNeedsReturnType");
            return null;
        }
        if (sourceBody instanceof JCTree.JCExpression) {
            if (shouldVerify) {
                body = compiler.expressionCompiler.toExpr((JCTree.JCExpression) sourceBody);
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
                    compiler.reportError(source, "bodylessMethodWithoutContract", methodSymbol.name.toString());
                }
            } else {
                if (!(source instanceof JCTree.JCFieldAccess fa && fa.sym instanceof Symbol.DynamicMethodSymbol) && externalContract != null) {
                    compiler.reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
                }
                if (contractOverride != null) {
                    header = contractOverride;
                } else {
                    header = new MethodOrLoopContract(source, true);
                }
                var allowFooter = JavaToDafnyCompiler.isConstructor(methodSymbol);
                var postHeader = new MethodOrLoopContractCompiler(compiler).
                        translateHeader((JCTree.JCBlock) sourceBody, header, allowFooter, true);
                if (postHeader.size() != 1) {
                    compiler.reportError(source, "pureMethodMultipleStatements");
                    return null;
                }

                var statement = postHeader.getFirst();
                if (shouldVerify) {
                    if (statement instanceof JCTree.JCReturn returnStatement) {
                        body = compiler.expressionCompiler.toExpr(returnStatement.expr);
                    } else {
                        compiler.reportError(source, "pureMethodNeedsReturnStatement");
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

    private void applyInvariants(JCTree source, JCTree.JCModifiers modifiers, Symbol.MethodSymbol methodSymbol, MethodOrLoopContract header) {
        boolean isPublic = (modifiers.flags & Flags.PUBLIC) != 0;
        boolean isStaticMethod = JavaToDafnyCompiler.isStatic(modifiers);

        // Only apply invariants to public instance methods (not static methods)
        if (isPublic && !isStaticMethod) {
            for (var invariant : invariants) {
                var memberName = compiler.nameCompiler.getCompiledName(invariant.sym);
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

    private @Nullable MethodOrLoopContract findExternalContract(Symbol.MethodSymbol methodSymbol) {
        var enclosingClass = methodSymbol.enclClass();
        var contractor = compiler.externalContractCompiler.externalContracts.get(enclosingClass);
        if (contractor != null) {
            return contractor.methodContracts().get(methodSymbol);
        }
        return null;
    }

    private List<Formal> getIns(Symbol.MethodSymbol methodSymbol, boolean shouldVerify, IOrigin bodyOrigin) {
        return methodSymbol.extraParams.
                appendList(methodSymbol.getParameters()).
                appendList(methodSymbol.capturedLocals).map(jvd -> {
                    var index = JVerifyIndex.instance(compiler.context);
                    var parameter = index.getTree(jvd);
                    IOrigin parameterOrigin;
                    if (parameter == null) {
                        parameterOrigin = bodyOrigin;
                    } else {
                        parameterOrigin = compiler.toOrigin(parameter);
                    }
                    Name formalName = new Name(parameterOrigin, compiler.nameCompiler.getCompiledName(jvd));
                    var syntacticType = compiler.translateMethodSignatureType(jvd.type, parameterOrigin, shouldVerify);
                    return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                            null, null, false, false, false, null);
                });
    }

    private Formal makeReturnFormal(IOrigin origin, Type syntacticType) {
        var name = new Name(origin, compiler.nameCompiler.RETURN_VARIABLE_NAME);
        return new Formal(origin, name, syntacticType, false, false, null, null, false, false, false, null);
    }
}
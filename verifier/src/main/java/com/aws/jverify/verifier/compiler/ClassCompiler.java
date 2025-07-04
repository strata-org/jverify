package com.aws.jverify.verifier.compiler;

import com.aws.jverify.Contract;
import com.aws.jverify.InheritContract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Pure;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.temporary.ClassesExtendingClassesCompiler;
import com.aws.jverify.verifier.compiler.temporary.RecordCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassCompiler {
    public final JavaToDafnyCompiler compiler;
    private Symbol.@Nullable ClassSymbol typeForWhichCurrentClassIsDefiningContract;
    private final List<Symbol.MethodSymbol> invariants = new ArrayList<>();
    private final List<JCTree.JCVariableDecl> initializers = new ArrayList<>();

    private final Map<Symbol.MethodSymbol, MemberDecl> intermediateMethodsToSymbols = new HashMap<>();
    private final ClassesExtendingClassesCompiler classDeclCompiler = new ClassesExtendingClassesCompiler(this);

    public ClassCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    List<? extends TopLevelDecl> translateTypeDeclaration(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {

            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));

            Name name = null;
            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                var contractee = compiler.getContractTarget(classDecl, contractAnnotation);
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
            } else {
                for(var member : classDecl.getMembers()) {
                    if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                        continue;
                    }
                    // Don't report errors when extracting this contract here,
                    // since the actual translation of the method will report them.
                    var header = compiler.extractContract(methodDecl, false);
                    compiler.methodContracts.put(methodDecl.sym, header);
                }
            }

            if (name == null) {
                name = compiler.getName(classDecl, classDecl.sym);
            }
            var origin = compiler.declToOrigin(classDecl, name);
            compiler.contextOrigins.push(origin);

            JavaToDafnyCompiler.ShouldVerifyMode mode = compiler.getShouldVerifyMode(annotationsByName);
            if (typeForWhichCurrentClassIsDefiningContract != null) {
                mode = JavaToDafnyCompiler.ShouldVerifyMode.AlwaysNo;
            }
            compiler.addShouldVerify(mode);


            List<TopLevelDecl> intermediateResult = switch (classDecl.getKind()) {
                case ENUM -> List.of(translateEnum(classDecl, origin, name));
                case INTERFACE, CLASS -> List.of(translateClass(classDecl, origin, name));
                case RECORD -> List.of(new RecordCompiler(this).translateRecord(classDecl, origin, name));
                case ANNOTATION_TYPE -> {
                    compiler.reportError(classDecl, "notSupported", "%s declaration".formatted(classDecl.getKind()));
                    yield List.of();
                }
                // JCClassDecl#getKind returns one of the above five values
                default -> throw new JavaViolationException("unexpected kind: " + classDecl.getKind());
            };

            List<TopLevelDecl> result = new ArrayList<>();
            for(var topLevelDecl : intermediateResult) {
                result.addAll(classDeclCompiler.compile(topLevelDecl));
            }
            
            typeForWhichCurrentClassIsDefiningContract = null;
            compiler.contextOrigins.pop();
            compiler.shouldVerifies.pop();
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
        for(var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl variableDecl) {
                var variableName = compiler.nameCompiler.getCompiledName(variableDecl.sym);
                Name constructorName = compiler.getName(variableDecl, variableName);
                constructors.add(new DatatypeCtor(compiler.declToOrigin(variableDecl, constructorName), constructorName,
                        null, false, List.of()));

            }
        }
        return new IndDatatypeDecl(origin, name, null, List.of(), List.of(), List.of(), constructors, false);
    }


    private ClassDecl translateClass(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
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
        var definingSymbol = getCurrentTypeSymbol(classDecl);

        Stream<com.sun.tools.javac.code.Type> baseTypes = definingSymbol.getInterfaces().stream();
        if (definingSymbol.getSuperclass() != null) {
            baseTypes = Stream.concat(Stream.of(definingSymbol.getSuperclass()), baseTypes);
        }
        var superTraits = baseTypes.
                filter(compiler::typeHasAContract).
                map((com.sun.tools.javac.code.Type type) -> compiler.translateType(null, type, origin)).
                collect(Collectors.<Type>toList());

        var typeParameters = translateTypeParameters(classDecl.typarams);
        return new ClassDecl(origin, new Name(name.getOrigin(), name.getValue()), null,
                typeParameters, members, superTraits, false);
    }

    public List<TypeParameter> translateTypeParameters(List<JCTree.JCTypeParameter> typarams) {
        return typarams.stream().map(p -> {
            var name = compiler.getName(p, p.getName());
            var bounds = p.bounds.map(compiler::translateType);
            return new TypeParameter(compiler.toOrigin(p),
                    name, null, TPVarianceSyntax.NonVariant_Strict,
                    new TypeParameterCharacteristics(
                            TypeParameterEqualitySupportValue.InferredRequired,
                            TypeAutoInitInfo.MaybeEmpty,
                            false
                    ),
                    bounds);
        }).toList();
    }

    public Symbol.ClassSymbol getCurrentTypeSymbol(JCTree.JCClassDecl classDecl) {
        return typeForWhichCurrentClassIsDefiningContract == null ? classDecl.sym : typeForWhichCurrentClassIsDefiningContract;
    }

    public MemberDecl translateMember(JCTree member) {
        switch (member) {
            case JCTree.JCClassDecl _ -> {
                return null;
            }
            case JCTree.JCMethodDecl method -> {
                MethodOrFunction methodOrFunction = translateMethodDecl(method);
                intermediateMethodsToSymbols.put(method.sym, methodOrFunction);
                return methodOrFunction;
            }
            case JCTree.JCVariableDecl variableDecl -> {
                return translateField(variableDecl);
            }
            default -> throw new JavaToDafnyCompiler.NotImplementedException(member.getClass().getName());
        }
    }

    public Field translateField(JCTree.JCVariableDecl variableDecl) {
        var varFlags = variableDecl.getModifiers().getFlags();
        Name fieldName = compiler.getName(variableDecl, variableDecl.sym);
        IOrigin origin = compiler.declToOrigin(variableDecl, fieldName);
        Type type = compiler.translateType(variableDecl.getModifiers(), variableDecl.vartype.type, compiler.toOrigin(variableDecl.vartype));
        if (variableDecl.getInitializer() != null) {
            if (varFlags.contains(Modifier.FINAL)) {
                var rhs = compiler.expressionCompiler.toExpr(variableDecl.getInitializer());
                var isStatic = varFlags.contains(Modifier.STATIC);
                return new ConstantField(origin, fieldName, compiler.getAttributes(origin), false, type, rhs, isStatic, false);
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
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));

        boolean shouldVerify = compiler.processVerifyAnnotationAndPop(annotationsByName);

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

        var methodCompiler = new BlockCompiler(compiler);
        var name = compiler.getName(source, methodSymbol);
        var origin = compiler.declToOrigin(source, name);
        var isStatic = JavaToDafnyCompiler.isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol);

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
                if (!(source instanceof JCTree.JCFieldAccess fa && fa.sym instanceof Symbol.DynamicMethodSymbol) && externalContract != null) {
                    compiler.reportError(externalContract.treeOrigin, "internalAndExternalContractForMethod", methodSymbol.name.toString());
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
            var returnType = compiler.translateType(methodSymbol.type.getReturnType(), bodyOrigin);
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


    private Function translatePureMethodOrLambda(JCTree source, JCTree.JCModifiers modifiers,
                                                 Symbol.MethodSymbol methodSymbol, JCTree sourceBody,
                                                 List<JCTree.JCTypeParameter> typeParameters, boolean shouldVerify,
                                                 @Nullable MethodOrLoopContract contractOverride) {
        var bodyOrigin = compiler.toOrigin(sourceBody);

        @Nullable MethodOrLoopContract externalContract = findExternalContract(methodSymbol);
        var methodCompiler = new BlockCompiler(compiler);
        var name = compiler.getName(source, methodSymbol);
        var origin = compiler.declToOrigin(source, name);
        var isStatic = JavaToDafnyCompiler.isStatic(modifiers);
        List<Formal> ins = getIns(methodSymbol);
        Expression body = null;
        MethodOrLoopContract header;
        var returnType = compiler.translateType(methodSymbol.type.getReturnType(), bodyOrigin);
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
                var postHeader = methodCompiler.translateHeader((JCTree.JCBlock) sourceBody, header, true);
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
            for(var invariant : invariants) {
                var memberName = compiler.nameCompiler.getCompiledName(invariant);
                var invariantName = compiler.getName(source, memberName);
                var invariantOrigin = compiler.declToOrigin(source, invariantName);
                ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                        memberName, null), null, new ActualBindings(List.of()), null);
                var invariantCall = new AttributedExpression(call,null, null);
                if (!JavaToDafnyCompiler.isConstructor(methodSymbol)) {
                    header.preconditions.add(invariantCall);
                }
                header.postconditions.add(invariantCall);
            }
        }
    }

    private @Nullable MethodOrLoopContract findExternalContract(Symbol.MethodSymbol methodSymbol) {
        var enclosingClass = methodSymbol.enclClass();
        var contractor = compiler.externalContracts.get(enclosingClass);
        if (contractor != null) {
            return contractor.methodContracts().get(methodSymbol);
        }
        return null;
    }

    private List<Formal> getIns(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getParameters().map(jvd -> {
            var trees = JavacTrees.instance(compiler.context);
            var parameter = trees.getTree(jvd);
            var parameterOrigin  = compiler.toOrigin(parameter);
            Name formalName = new Name(parameterOrigin, jvd.name.toString());
            var syntacticType = compiler.translateType(jvd.type, parameterOrigin);
            return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                    null, null, false, false, false, null);
        });
    }

    private Formal makeReturnFormal(IOrigin origin, Type syntacticType) {
        var name = new Name(origin, compiler.nameCompiler.METHOD_RETURN_VARIABLE_NAME);
        return new Formal(origin, name, syntacticType, false, false, null, null, false, false, false, null);
    }
}
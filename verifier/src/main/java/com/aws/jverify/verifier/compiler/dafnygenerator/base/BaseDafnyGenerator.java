package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.*;

import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.DafnyGenerator;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;

import com.sun.tools.javac.tree.TreeInfo;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.util.Names;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class BaseDafnyGenerator implements DafnyGenerator {
    public static final boolean Ghostness = true;
    public static final String PURE_OBJECT_NAME = "Object";
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public static final String JVERIFY_PACKAGE = JVerify.class.getPackageName();
    public final Context context;

    public final Set<Symbol.MethodSymbol> symbolsWithAContract = new HashSet<>();
    public final NameCompiler nameCompiler;
    public final TypeDeclarationCompiler typeDeclarationCompiler;
    private final MethodOrLoopContractCompiler methodOrLoopContractCompiler;
    public final Reporter reporter;
    public final Names names;
    public final JavacElements elements;
    public final VerifierOptions verifierOptions;
    public final JVerifyIndex index;
    private final DafnyGenerator generator;

    /**
     * Edges are from child to parent types, similar to the references in the code
     */
    private final Graph<Symbol.ClassSymbol, DefaultEdge> typeHierarchy = new DefaultDirectedGraph<>(DefaultEdge.class);
    public Map<JCTree.JCCompilationUnit, List<TopLevelDecl>> declarationsForFile = new HashMap<>();
    public final ExpressionCompiler expressionCompiler;
    
    private boolean translatingVerifiedMethodSignature;
    
    public BaseDafnyGenerator(Context context) {
        context.put(BaseDafnyGenerator.class, this);
        this.context = context;
        this.verifierOptions = context.get(VerifierOptions.class);

        expressionCompiler = new ExpressionCompiler(context, this);
        elements = JavacElements.instance(context);
        reporter = Reporter.instance(context);
        nameCompiler = NameCompiler.instance(context);
        typeDeclarationCompiler = new TypeDeclarationCompiler(context);
        methodOrLoopContractCompiler = MethodOrLoopContractCompiler.instance(context);
        index = JVerifyIndex.instance(context);
        names = Names.instance(context);
        generator = context.get(DafnyGenerator.class);
    }

    public NameCompiler getNameCompiler() {
        return nameCompiler;
    }

    public FilesContainer generateDafny(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {
        /* TODO: confirm this is solved
         * Dafny currently has a bug that will be fixed by this PR: https://github.com/dafny-lang/dafny/pull/6214
         * To work around this bug, the built-in contracts file must be serialized after
         * its users. Because the 's' of 'string://' comes after 'file://', sorting by name achieves this
         */
        parsed.sort(Comparator.comparing(f -> f.getSourceFile().toUri().toString()));

        Map<Symbol.ClassSymbol, JCTree.JCCompilationUnit> symbolToCompilationUnit = new HashMap<>();
        for (var compilationUnit : parsed) {
            var hierarchyScanner = new TreeScanner() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl tree) {
                    addHierarchyForSymbol(tree.sym);
                    symbolToCompilationUnit.put(tree.sym, compilationUnit);
                    super.visitClassDef(tree);
                }
            };

            declarationsForFile.put(compilationUnit, new ArrayList<>());
            nameCompiler.visitTopLevel(compilationUnit);
            hierarchyScanner.visitTopLevel(compilationUnit);
        }

        // Add a default origin to fallback to
        var dummyToken = new Token(1, 1);
        reporter.contextOrigins.push(new TokenRangeOrigin(dummyToken, dummyToken));

        compileSymbolsTopologically(symbolToCompilationUnit);

        reporter.contextOrigins.pop();

        List<FileHeader> filesStarts = new ArrayList<>();
        for (var compilationUnit : parsed) {
            List<TopLevelDecl> fileDeclarations = declarationsForFile.get(compilationUnit);
            var isLibrary = libraries.contains(compilationUnit);
            fileDeclarations.sort(Comparator.comparing(t -> {
                var startToken = switch(t.getOrigin()) {
                    case SourceOrigin sourceOrigin -> sourceOrigin.getReportingRange().getStartToken();
                    case TokenRangeOrigin tokenRangeOrigin -> tokenRangeOrigin.getStartToken();
                    default -> throw new RuntimeException();
                };
                return compilationUnit.getLineMap().getPosition(startToken.getLine(), startToken.getCol());
            }));
            filesStarts.add(new FileHeader(compilationUnit.sourcefile.toUri().toString(), isLibrary, fileDeclarations));
        }

        return new FilesContainer(filesStarts);
    }

    @Override
    public List<Statement> translateStatementAfterLabel(BlockCompiler blockCompiler, JCTree.JCStatement statement, List<Label> labels, IOrigin origin) {
        return blockCompiler.translateStatementAfterlabel(statement, labels, origin);
    }

    @Override
    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        return expressionCompiler.toExprBase(expr, originOverride, context);
    }

    private void compileSymbolsTopologically(Map<Symbol.ClassSymbol, JCTree.JCCompilationUnit> symbolToCompilationUnit) {
        var iterator = new TopologicalOrderIterator<>(this.typeHierarchy);
        var itemsFromChildrenToParents = new ArrayList<Symbol.ClassSymbol>();
        iterator.forEachRemaining(itemsFromChildrenToParents::add);
        for(var currentTypeSymbol : itemsFromChildrenToParents.reversed()) {
            var relatedDeclaration = index.getTree(currentTypeSymbol);
            if (relatedDeclaration == null) {
                continue;
            }
            var compilationUnit = symbolToCompilationUnit.get(currentTypeSymbol);
            
            JVerifyIndex index = JVerifyIndex.instance(context);
            Env<AttrContext> env = index.getEnv(currentTypeSymbol);
            if (env != null) {
                compilationUnit = env.toplevel;
            }
            reporter.compilationUnit = compilationUnit;
            var dafnyDecls = typeDeclarationCompiler.translateTypeDeclaration(relatedDeclaration);
            declarationsForFile.get(compilationUnit).addAll(dafnyDecls);
        }
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
    
    public @Nullable Type translateType(JCTree tree) {
        return generator.translateType(tree.type, reporter.toOrigin(tree), null);
    }

    public @Nullable Type translateMethodSignatureType(com.sun.tools.javac.code.Type type, IOrigin origin, boolean willVerify) {
        translatingVerifiedMethodSignature = willVerify;
        var result = generator.translateType(type, origin, null);
        translatingVerifiedMethodSignature = false;
        return result;
    }
    
    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin) {
        return generator.translateType(type, origin, null);
    }

    @Nullable
    public Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin, JCTree.JCModifiers additionalModifiers) {

        var primitiveTypeKind = toPrimitiveType(type);
        if (primitiveTypeKind != null) {
            return translatePrimitiveType(type, origin, primitiveTypeKind);
        }

        switch (type) {
            case com.sun.tools.javac.code.Type.ArrayType arrayTypeTree -> {
                return generator.translateArrayType(arrayTypeTree, origin, additionalModifiers);
            }
            case com.sun.tools.javac.code.Type.IntersectionClassType _ -> {
                return null;
            }
            case com.sun.tools.javac.code.Type.ClassType classType -> {
                return generator.translateClassType(origin, additionalModifiers, classType);
            }
            case com.sun.tools.javac.code.Type.CapturedType capturedType -> {
                // TODO needs further investigation
                return new UserDefinedType(origin, new NameSegment(origin, PURE_OBJECT_NAME, null));
            }
            case com.sun.tools.javac.code.Type.TypeVar typeVar -> {
                return new UserDefinedType(origin, new NameSegment(origin, nameCompiler.getCompiledName(typeVar.tsym, origin), null));
            }
            case com.sun.tools.javac.code.Type.WildcardType wildcardType -> {
                return translateWildcardType(origin, wildcardType);
            }
            default -> {
            }
        }
        reporter.reportError(origin, "notSupported", "type " + type.getClass().getName());
        return null;
    }
    
    public UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayTypeTree,
                                              IOrigin origin,
                                              JCTree.JCModifiers additionalModifiers) {
        var elemType = generator.translateType(arrayTypeTree.elemtype, origin, additionalModifiers);
        if (elemType == null) {
            // should be unreachable
            throw new IllegalArgumentException("Array type without element type");
        }
        Symbol.ClassSymbol arraySymbol = elements.getTypeElement(ArrayCompiler.COM_AWS_JVERIFY_BUILTIN_JARRAY);
        var arrayDafnyName = nameCompiler.getCompiledName(arraySymbol, origin);
        return new UserDefinedType(origin, new NameSegment(origin, arrayDafnyName, List.of(elemType)));
    }

    private NonProxyType translatePrimitiveType(com.sun.tools.javac.code.Type type, IOrigin origin, TypeKind primitiveTypeKind) {
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
                        reporter.reportError(origin, "unboundedNonInt", type.toString());
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
                return getChar16Type(origin);
            }
            case FLOAT -> {
                reporter.reportError(origin, "notSupported", "float type");
                return null;
            }
            case DOUBLE -> {
                return new Fp64Type(origin);
            }
        }

        reporter.reportError(origin, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
        return null;
    }

    public static UserDefinedType getChar16Type(IOrigin origin) {
        return new UserDefinedType(origin, new NameSegment(origin, "char16", null));
    }

    private Type translateWildcardType(IOrigin origin, com.sun.tools.javac.code.Type.WildcardType wildcardType) {
        var extendsBound = wildcardType.getExtendsBound();
        if (extendsBound != null) {
            if (extendsBound instanceof com.sun.tools.javac.code.Type.IntersectionClassType intersectionClassType) {
                // TODO add test
                return translateType(intersectionClassType.getComponents().getFirst(), origin);
            }
            return translateType(extendsBound, origin);
        }
        var superBound = wildcardType.getSuperBound();
        if (superBound != null) {
            if (translatingVerifiedMethodSignature) {
                reporter.reportError(origin, "notSupported", "keyword 'super' in method signature");
            }
            if (superBound instanceof com.sun.tools.javac.code.Type.IntersectionClassType intersectionClassType) {
                // TODO add test
                return translateType(intersectionClassType.getComponents().getFirst(), origin);
            }
            return translateType(superBound, origin);
        }
        return new UserDefinedType(origin, new NameSegment(origin, PURE_OBJECT_NAME, null));
    }

    public Type translateClassType(IOrigin origin,
                                   JCTree.JCModifiers additionalModifiers,
                                   com.sun.tools.javac.code.Type.ClassType classType) {
        // Remove the name qualification because we do not support that yet
        var compiledName = nameCompiler.getCompiledName(classType.tsym, origin);
        var typeArgumentsStream = classType.getTypeArguments().stream().map(a -> translateType(a, origin, null));
        if (classType.tsym.isDirectlyOrIndirectlyLocal()) {
            var ownerTypes = getOwnAndEnclosedTypeParameters(classType.tsym).toList();
            Stream<Type> typeStream = ownerTypes.stream().map(tp -> translateType(tp.type, reporter.toOrigin(tp)));
            typeArgumentsStream = Stream.concat(typeStream, typeArgumentsStream);
        }
        var typeArguments = typeArgumentsStream.toList();
        if (typeArguments.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        if (typeArguments.isEmpty()) {
            if (classType.tsym.getTypeParameters().isEmpty()) {
                typeArguments = null;
            } else {
                // unresolved raw type
                var objectType = translateType(Symtab.instance(context).objectType, origin);
                typeArguments = IntStream.range(0, classType.tsym.getTypeParameters().size()).
                        mapToObj(_ -> objectType).toList();
            }
        }
        return new UserDefinedType(origin, new NameSegment(origin, compiledName, typeArguments));
    }

    @Override
    public AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context) {
        return expressionCompiler.translateNewClassToAssignmentRhs(newClass, origin, context);
    }

    /**
     * If the specified tree represents a primitive type,
     * returns the corresponding {@link TypeKind},
     * otherwise returns {@code null}.
     */
    @Nullable
    public TypeKind toPrimitiveType(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.JCVoidType) {
            return TypeKind.VOID;
        } else if (type instanceof com.sun.tools.javac.code.Type.JCPrimitiveType primitiveType) {
            return primitiveType.getKind();
        }

        return null;
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

    public boolean isPure(Symbol.ClassSymbol classSymbol) {
        Symtab symtab = Symtab.instance(context);
        if (classSymbol.type == symtab.objectType) {
            return true;
        }
        if (JVerifyUtils.isInterface(classSymbol) && !JVerifyUtils.isAnnotated(classSymbol.type, Impure.class)) {
            return true;
        }
        boolean anonymousImmutableType = isAnonymousOrFinalImmutableType(classSymbol);
        if (anonymousImmutableType) {
            return true;
        }
        return JVerifyUtils.isRecord(classSymbol.type) || isImmutableClass(classSymbol);
    }

    /**
     * Right now we implicitly consider anonymous and final types immutable,  
     * if they only have final fields and inherit from immutable types
     */
    public boolean isAnonymousOrFinalImmutableType(Symbol.ClassSymbol classSymbol) {
        if (classSymbol.isAnonymous() || (classSymbol.isFinal() && classSymbol.name.startsWith(names.lambda))) {
            if (hasNonFinalFields(classSymbol)) {
                return false;
            }
            com.sun.tools.javac.code.Type superclass = classSymbol.getSuperclass();
            if (superclass != null && superclass.tsym != null) {
                if (!isPure((Symbol.ClassSymbol) superclass.tsym)) {
                    return false;
                }
            }
            return classSymbol.getInterfaces().stream().
                    allMatch(it -> isPure((Symbol.ClassSymbol) it.tsym));
        }
        return false;
    }
    
    public boolean hasNonFinalFields(Symbol.ClassSymbol classSymbol) {
        for (Symbol member : classSymbol.getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD &&
                    (member.flags() & Flags.FINAL) == 0) {
                return true;
            }
        }
        return false;
    }

    public Stream<JCTree.JCTypeParameter> getOwnAndEnclosedTypeParameters(Symbol symbol) {
        if (symbol instanceof Symbol.ClassSymbol classSymbol) {
            return getOwnAndEnclosedTypeParameters(classSymbol);
        } else if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
            return getOwnAndEnclosedTypeParameters(methodSymbol);
        } else if (symbol instanceof Symbol.PackageSymbol) {
            return Stream.empty();
        } else if (symbol instanceof Symbol.VarSymbol varSymbol) {
            return getOwnAndEnclosedTypeParameters(varSymbol.owner);
        }
        throw new RuntimeException();
    }

    Stream<JCTree.JCTypeParameter> getOwnAndEnclosedTypeParameters(Symbol.ClassSymbol classSymbol) {
        var trees = JVerifyIndex.instance(context);
        JCTree.JCClassDecl decl = (JCTree.JCClassDecl)trees.getTree(classSymbol);
        if (decl == null) {
            // ObjectContract
            return Stream.empty();
        }
        var mine = decl.getTypeParameters().stream();
        if (classSymbol.owner == null) {
            return mine;
        }
        return Stream.concat(mine, getOwnAndEnclosedTypeParameters(classSymbol.owner));
    }

    Stream<JCTree.JCTypeParameter> getOwnAndEnclosedTypeParameters(Symbol.MethodSymbol methodSymbol) {
        var trees = JVerifyIndex.instance(context);
        JCTree.JCMethodDecl decl = (JCTree.JCMethodDecl)trees.getTree(methodSymbol);
        var mine = decl.getTypeParameters().stream();
        if (methodSymbol.owner == null) {
            return mine;
        }
        return Stream.concat(mine, getOwnAndEnclosedTypeParameters(methodSymbol.owner));
    }
    
    public boolean isImmutableClass(Symbol.ClassSymbol classSymbol) {
        var decl = (JCTree.JCClassDecl)index.getTree(classSymbol);
        boolean immutableClass = false;
        if (decl != null) {
            var contract = classSymbol.getAnnotation(Contract.class);
            if (contract != null) {
                immutableClass = contract.pure();
            }
        }
        return immutableClass;
    }


    public void fillDafnyContract(
        JCTree.JCStatement outerBlock,
        MethodOrLoopDafnyContract dafnyContract)
    {
        var contract = methodOrLoopContractCompiler.getContract(outerBlock);
        for(var precondition : contract.preconditions()) {
            dafnyContract.preconditions.add(new AttributedExpression(
                    expressionCompiler.toExpr(precondition.get(), ExpressionContext.Pure), null, null));
        }
        for(var postcondition : contract.postconditions()) {
            if (handlePostcondition(dafnyContract, postcondition.get())) {
                break;
            }
        }
        for(var javaLoopInvariant : contract.loopInvariants()) {
            dafnyContract.loopInvariants.add(new AttributedExpression(
                    expressionCompiler.toExpr(javaLoopInvariant.get(), ExpressionContext.Pure), null, null));
        }
        dafnyContract.decreases.addAll(contract.decreases().stream().map(d ->
                expressionCompiler.toExpr(d, ExpressionContext.Pure)).toList());
        for(var readsJavaExpr : contract.reads()) {
            Expression readsExpr = expressionCompiler.toExpr(readsJavaExpr.get(), ExpressionContext.Pure);
            dafnyContract.reads.add(new FrameExpression(readsExpr.getOrigin(), readsExpr, null));
        }
        for(var modifiesJavaExpr : contract.modifies()) {
            Expression modifiesExpr = expressionCompiler.toExpr(modifiesJavaExpr.get(), ExpressionContext.Pure);
            dafnyContract.modifies.add(new FrameExpression(modifiesExpr.getOrigin(), modifiesExpr, null));
        }

        var implementation = MethodOrLoopContractCompiler.getImplementationStatements(outerBlock);
        if (dafnyContract.isPure) {
            if (!implementation.isEmpty()) {
                dafnyContract.pureBody = expressionCompiler.toExprWithFlows(implementation, ExpressionContext.Pure);
            }
        }
    }

    private boolean handlePostcondition(MethodOrLoopDafnyContract header, JCTree.JCExpression expr) {
        switch (expr) {
            case null -> {
                return true;
            }
            case JCTree.JCLambda lambda -> {
                if (lambda.getParameters().size() != 1) {
                    throw new JavaViolationException("A postcondition call lambda must take exactly one argument");
                }
                var parameter = lambda.params.getFirst();
                var origin = reporter.toOrigin(lambda);
                var paramName = parameter.getName().toString();
                var type = translateType(parameter.type, reporter.toOrigin(parameter), null);

                var returnVar = new BoundVar(origin, new Name(origin, paramName), type, false);
                var lhs = new CasePattern<>(origin, paramName, returnVar, null);
                var rhs = TreeInfo.isConstructor(header.treeOrigin)
                        ? new ThisExpr(origin)
                        : new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
                Expression origCondition;
                if (lambda.getBody() instanceof JCTree.JCExpression expressionBody) {
                    origCondition = expressionCompiler.toExpr(expressionBody, ExpressionContext.Pure);
                } else if (lambda.getBody() instanceof JCTree.JCBlock block) {
                    origCondition = expressionCompiler.stmtToExpr(block, ExpressionContext.Pure);
                } else {
                    throw new RuntimeException("impossible");
                }
                var condition = new LetExpr(origin, List.of(lhs), List.of(rhs), origCondition, true, null);
                header.postconditions.add(new AttributedExpression(condition, null, null));

            }
            case JCTree.JCMemberReference memberReference -> {
                var origin = reporter.toOrigin(memberReference);
                NameSegment arg = new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
                var callee = new ExprDotName(origin,
                        expressionCompiler.toExpr(memberReference.expr, ExpressionContext.Pure),
                        reporter.getName(memberReference, nameCompiler.getCompiledName(memberReference.sym, origin)), null);
                var call = ExpressionCompiler.createCall2(origin, callee, Stream.of(arg));
                header.postconditions.add(new AttributedExpression(call, null, null));
            }
            case JCTree.JCTypeCast typeCast ->
                // Casts like (IntPredicate) are sometimes necessary to disambiguate
                    handlePostcondition(header, typeCast.getExpression());
            default -> {
                var dafnyExpr = expressionCompiler.toExpr(expr, ExpressionContext.Pure);
                header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
            }
        }
        return false;
    }
}


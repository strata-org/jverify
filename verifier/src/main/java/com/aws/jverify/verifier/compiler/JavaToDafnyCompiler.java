package com.aws.jverify.verifier.compiler;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.simplifications.ExternalContractCompiler;
import com.aws.jverify.verifier.compiler.simplifications.LambdaCompiler;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;

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

import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public static final String REFERENCE_OR_VALUE_OBJECT_NAME = "Object";
    public static final String REFERENCE_OBJECT_NAME = "ModifiableObject";
    public final Context context;

    public final Set<Symbol.MethodSymbol> symbolsWithAContract = new HashSet<>();
    public final Stack<IOrigin> contextOrigins = new Stack<>();
    public final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    public final NameCompiler nameCompiler;
    public final ExternalContractCompiler externalContractCompiler = new ExternalContractCompiler(this);
    public final VerifyAnnotationCompiler verifyAnnotationCompiler;
    public final LambdaCompiler lambdaCompiler;
    public JCDiagnostic.Factory diagnosticFactory;
    public final VerifierOptions verifierOptions;

    /**
     * Edges are from child to parent types, similar to the references in the code
     */
    private final Graph<Symbol.ClassSymbol, DefaultEdge> typeHierarchy = new DefaultDirectedGraph<>(DefaultEdge.class);
    public Map<JCTree.JCCompilationUnit, List<TopLevelDecl>> declarationsForFile = new HashMap<>();
    public final ExpressionCompiler expressionCompiler = new ExpressionCompiler(this);
    
    public JCTree.JCCompilationUnit compilationUnit;
    private boolean translatingVerifiedMethodSignature;
    public SourceFile builtinSource;
    public static final String builtinFile = "/builtin-contracts.java";
    public static final String objectFile = "/object-contract.java";
    private boolean skipDiagnostics;
    
    public JavaToDafnyCompiler(Context context, VerifierOptions verifierOptions) {
        this.context = context;
        this.verifierOptions = verifierOptions;
        nameCompiler = new NameCompiler(externalContractCompiler);
        diagnosticFactory = JCDiagnostic.Factory.instance(context);
        verifyAnnotationCompiler = new VerifyAnnotationCompiler(this);
        lambdaCompiler = new LambdaCompiler(this);
    }

    public NameCompiler getNameCompiler() {
        return nameCompiler;
    }
    
    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, List<JavaFileObject> files) {
        if (options.includeBuiltinContracts()) {
            builtinSource = new SourceFile(JavaToDafnyCompiler.builtinFile, Common.getResourceFile(getClass(), JavaToDafnyCompiler.builtinFile));
            files.add(builtinSource);
        }
        var contractSource = new SourceFile(JavaToDafnyCompiler.objectFile, Common.getResourceFile(getClass(), JavaToDafnyCompiler.objectFile));
        files.add(contractSource);
        
        Set<JCTree.JCCompilationUnit> parsedSet = new JavaFrontEnd(this).parseResolveAndDesugarJava(options, files);
        if (parsedSet == null) {
            return new FilesContainer(List.of());
        }

        var parsed = new ArrayList<>(parsedSet);

        /*
         * Dafny currently has a bug that will be fixed by this PR: https://github.com/dafny-lang/dafny/pull/6214
         * To work around this bug, the built-in contracts file must be serialized after 
         * its users. Because the 's' of 'string://' comes after 'file://', sorting by name achieves this
         */
        parsed.sort(Comparator.comparing(f -> f.getSourceFile().toUri().toString()));

        var foundClassSymbols = new HashMap<Symbol.ClassSymbol, JCTree.JCCompilationUnit>();
        for (var compilationUnit : parsed) {
            externalContractCompiler.discoverTypesAndContractClasses(compilationUnit, foundClassSymbols);
            declarationsForFile.put(compilationUnit, new ArrayList<>());
        }
        
        for(var foundClassSymbol : foundClassSymbols.keySet()) {
            addHierarchyForSymbol(foundClassSymbol);
            nameCompiler.registerClass(foundClassSymbol);
        }

        externalContractCompiler.registerExternalContracts();

        // Add a default origin to fallback to
        var dummyToken = new Token(1, 1);
        contextOrigins.push(new TokenRangeOrigin(dummyToken, dummyToken));

        compileSymbolsTopologically(foundClassSymbols);

        contextOrigins.pop();

        List<FileHeader> filesStarts = new ArrayList<>();
        for (var compilationUnit : parsed) {
            List<TopLevelDecl> fileDeclarations = declarationsForFile.get(compilationUnit);
            var isLibrary = isLibrary(compilationUnit);
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

    private boolean isLibrary(JCTree.JCCompilationUnit compilationUnit) {
        return compilationUnit.getSourceFile() == builtinSource;
    }

    private void compileSymbolsTopologically(Map<Symbol.ClassSymbol, JCTree.JCCompilationUnit> symbolToCompilationUnit) {
        var iterator = new TopologicalOrderIterator<>(this.typeHierarchy);
        var itemsFromChildrenToParents = new ArrayList<Symbol.ClassSymbol>();
        iterator.forEachRemaining(itemsFromChildrenToParents::add);
        for(var currentTypeSymbol : itemsFromChildrenToParents.reversed()) {
            var relatedDeclarations = externalContractCompiler.declarationsForSymbolContract.get(currentTypeSymbol);
            if (relatedDeclarations == null) {
                continue;
            }
            compilationUnit = symbolToCompilationUnit.get(currentTypeSymbol);
            for(var relatedDeclaration : relatedDeclarations) {
                JVerifyIndex index = JVerifyIndex.instance(context);
                Env<AttrContext> env = index.getEnv(relatedDeclaration.sym);
                if (env != null) {
                    compilationUnit = env.toplevel;
                }
                var verifyAnnotation = compilationUnit.packge.getAnnotation(Verify.class);
                verifyAnnotationCompiler.processVerifyAnnotation(verifyAnnotation);
                var dafnyDecls = new ClassCompiler(this).translateTypeDeclaration(relatedDeclaration);
                verifyAnnotationCompiler.shouldVerifies.pop();
                declarationsForFile.get(compilationUnit).addAll(dafnyDecls);
            }
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

    public static Symbol.ClassSymbol getClassSymbol(JCTree.JCExpression valueArgument) {
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

    public void reportError(IOrigin origin, String key, Object... args) {
        reportError(positionFromOrigin(origin), key, args);
    }

    private JCDiagnostic.DiagnosticPosition positionFromOrigin(IOrigin origin) {
        return new DiagnosticPositionFromOrigin(originToRange(origin), compilationUnit.lineMap);
    }

    public void reportError(JCTree tree, String key, Object... args) {
        reportError(positionFromNode(tree, compilationUnit), key, args);
    }

    /**
     * In case of synthetic program nodes, which may occur nodes that are copies of source nodes that occur elsewhere in the program,
     * this method can be used not to generate diagnostics when processing the synthetic node.
     */
    public <T> T withSkipDiagnostics(Supplier<T> supplier, boolean skipDiagnostics) {
        var previous = this.skipDiagnostics;
        this.skipDiagnostics = skipDiagnostics;
        var result = supplier.get();
        this.skipDiagnostics = previous;
        return result;
    }
    
    private void reportError(JCDiagnostic.DiagnosticPosition position, String key, Object... args) {
        if (this.skipDiagnostics) {
            return;
        }
        this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                new DiagnosticSource(compilationUnit.getSourceFile(), null), position, key,
                args));
    }

    public static Map<String, JCTree.JCAnnotation> getAnnotationsByName(JCTree.JCModifiers modifiers) {
        var classAnnotations = modifiers.getAnnotations();
        return classAnnotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));
    }
    
    public static Map<String, JCTree.JCExpression> getArguments(JCTree.JCAnnotation annotation) {
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

    public static boolean isInterface(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.INTERFACE) != 0;
    }

    private static boolean isAbstract(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.ABSTRACT) != 0;
    }

    public static boolean isInterfaceOrAbstract(Symbol.ClassSymbol classDecl) {
        return isInterface(classDecl) || isAbstract(classDecl);
    }

    public static Object getLiteralValue(JCTree.JCExpression expression) {
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

    public boolean typeHasAContract(com.sun.tools.javac.code.Type type) {
        return externalContractCompiler.externalContracts.containsKey(type.tsym) || typeHasSource(type.tsym);
    }

    public boolean typeHasSource(Symbol.TypeSymbol typeSymbol) {
        var index = JVerifyIndex.instance(context);
        return index.getTree(typeSymbol) != null;
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

    static class NotImplementedException extends RuntimeException {
        public NotImplementedException(String message) {
            super(message);
        }
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
     * Returns {@code true} if the given type or any of its supertypes is annotated with the given annotation class.
     */
    public boolean isAnnotatedRecursive(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        var types = Types.instance(context);
        return types.closure(type).stream().anyMatch(t -> isAnnotated(t, clazz));
    }
    
    /**
     * Returns {@code true} if the given type is annotated with the given annotation class.
     */
    public boolean isAnnotated(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        var metadata = type.getMetadata(TypeMetadata.Annotations.class);
        // In some JDK distributions, this conditional is necessary to detect the annotation.
        if (metadata != null && metadata.annotationBuffer().stream()
                .anyMatch(s -> s.type.tsym.getQualifiedName().contentEquals(clazz.getName()))) {
            return true;
        }
        return type.getAnnotation(clazz) != null || type.tsym.getAnnotation(clazz) != null;
    }

    public boolean isSynthetic(JCTree methodNode, Symbol.MethodSymbol methodSymbol) {
        var containerPos = JVerifyIndex.instance(context).getTree(methodSymbol.enclClass()).pos;
        return methodNode.pos == containerPos;
    }

    public static boolean isStatic(JCTree.JCModifiers modifiers) {
        return (modifiers.flags & Flags.STATIC) == Flags.STATIC;
    }

    private JCDiagnostic.DiagnosticPosition positionFromNode(JCTree node, JCTree.JCCompilationUnit compilationUnit) {
        Objects.requireNonNull(node);
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

    public static LiteralExpr getHole(IOrigin origin) {
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
        return translateType(tree.type, toOrigin(tree), null);
    }

    public @Nullable Type translateMethodSignatureType(com.sun.tools.javac.code.Type type, IOrigin origin, boolean willVerify) {
        translatingVerifiedMethodSignature = willVerify;
        var result = translateType(type, origin, null);
        translatingVerifiedMethodSignature = false;
        return result;
    }
    
    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin) {
        return translateType(type, origin, null);
    }

    @Nullable
    public Type translateType(com.sun.tools.javac.code.Type type, IOrigin origin, JCTree.JCModifiers additionalModifiers) {
        // In several cases annotations that come right before types
        // end up bound to tree nodes such as variable declarations instead of the type.
        // Hence, for something like `@Nullable int[] foo;`, which should be interpreted as `(@Nullable int)[] foo;`,
        // we apply the modifier to the innermost element type of an array type.
        var isNullable = isNullable(type) || (isNullable(additionalModifiers) && !(type instanceof com.sun.tools.javac.code.Type.ArrayType));
        var nullableSuffix = isNullable ? "?" : "";

        var primitiveTypeKind = toPrimitiveType(type);
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
                var elemType = translateType(arrayTypeTree.elemtype, origin, additionalModifiers);
                if (elemType == null) {
                    // should be unreachable
                    throw new IllegalArgumentException("Array type without element type");
                }
                return new UserDefinedType(origin, new NameSegment(origin, "array" + nullableSuffix, List.of(elemType)));
            }
            case com.sun.tools.javac.code.Type.ClassType classType -> {

                var mirrors = type.getAnnotationMirrors();
                var modifiableAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Modifiable.class.getName())).findFirst();
                
                Symtab symtab = Symtab.instance(context);
                if (modifiableAnnotation.isPresent() || isAnnotated(additionalModifiers, com.aws.jverify.Modifiable.class)) {
                    if (classType.tsym == symtab.objectType.tsym) {
                        return new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OBJECT_NAME, null));
                    } else {
                        reportError(origin, "notSupported", "@Modifiable on a type other than Object");
                    }
                }
                if (classType.tsym == symtab.objectType.tsym) {
                    return new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OR_VALUE_OBJECT_NAME, null));
                }
                var className = classType.asElement().flatName();
                if (className.toString().equals(String.class.getName())) {
                    if (!isNullable) {
                        return new UserDefinedType(origin, new NameSegment(origin, "DString", null));
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
                    var arguments = classType.getTypeArguments().stream().map(a -> translateType(a, origin)).toList();
                    return new SeqType(origin, arguments);
                }
                
                // Remove the name qualification because we do not support that yet
                var compiledName = nameCompiler.getCompiledName(classType.tsym);
                var arguments = classType.getTypeArguments().stream().map(a -> translateType(a, origin, null)).toList();
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
            case com.sun.tools.javac.code.Type.WildcardType wildcardType -> {
                var extendsBound = wildcardType.getExtendsBound();
                if (extendsBound != null) {
                    return translateType(extendsBound, origin);
                }
                var superBound = wildcardType.getSuperBound();
                if (superBound != null) {
                    if (translatingVerifiedMethodSignature) {
                        reportError(origin, "notSupported", "keyword 'super' in method signature");
                    }
                    return translateType(superBound, origin);
                }
                Symtab symtab = Symtab.instance(context);
                return new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OR_VALUE_OBJECT_NAME, null));
            }
            default -> {
            }
        }
        reportError(origin, "notSupported", "type " + type.getClass().getName());
        return null;
    }

    /**
     * If the specified tree represents a primitive type,
     * returns the corresponding {@link TypeKind},
     * otherwise returns {@code null}.
     */
    private @Nullable TypeKind toPrimitiveType(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.JCVoidType) {
            return TypeKind.VOID;
        } else if (type instanceof com.sun.tools.javac.code.Type.JCPrimitiveType primitiveType) {
            return primitiveType.getKind();
        }

        return null;
    }

    public Name getName(JCTree tree, com.sun.tools.javac.util.Name name) {
        return getName(tree, name.toString());
    }

    public Name getName(JCTree tree, Symbol symbol) {
        return getName(tree, nameCompiler.getCompiledName(symbol), symbol.name.length());
    }

    public Name getName(JCTree tree, String name) {
        return getName(tree, name, name.length());
    }

    // TODO: Should be calling toOrigin() instead of duplicating the logic
    public Name getName(JCTree tree, String name, int length) {
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

    public SourceOrigin declToOrigin(JCTree node, Name name) {
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

    public static Function equalsFunctionDeclaration(IOrigin origin) {
        var otherIn = new Formal(origin, new Name(origin, "other"), new UserDefinedType(origin, new NameSegment(origin, "Object", null)),
                false, true, null, null, false, false, false, null);
        return new Function(origin, new Name(origin, "equals"), null, false, null, List.of(),
                List.of(otherIn),
                List.of(), List.of(), new Specification<>(List.of(), null),
                new Specification<>(List.of(), null), false, false, null, new BoolType(origin),
                null, null, null);
    }
}


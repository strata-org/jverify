package com.aws.jverify.verifier.compiler;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.frontend.JavaFrontEnd;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.code.Types;
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
import javax.tools.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JavaToDafnyCompiler {
    public static final String REFERENCE_OR_VALUE_OBJECT_NAME = "Object";
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    public final Context context;

    public final Set<Symbol.MethodSymbol> symbolsWithAContract = new HashSet<>();
    public final NameCompiler nameCompiler;
    public final VerifyAnnotationCompiler verifyAnnotationCompiler;
    public final TypeDeclarationCompiler typeDeclarationCompiler;
    public final Reporter reporter;
    public final Names names;
    public final VerifierOptions verifierOptions;
    public final JVerifyIndex index;

    /**
     * Edges are from child to parent types, similar to the references in the code
     */
    private final Graph<Symbol.ClassSymbol, DefaultEdge> typeHierarchy = new DefaultDirectedGraph<>(DefaultEdge.class);
    private final MissingContractsCompiler missingContractsCompiler;
    public Map<JCTree.JCCompilationUnit, List<TopLevelDecl>> declarationsForFile = new HashMap<>();
    public final ExpressionCompiler expressionCompiler = new ExpressionCompiler(this);
    
    private boolean translatingVerifiedMethodSignature;
    public SourceFile builtinSource;
    public static final String builtinFile = "/builtin-contracts.java";
    public static final String objectFile = "/object-contract.java";
    
    public JavaToDafnyCompiler(Context context, VerifierOptions verifierOptions) {
        this.context = context;
        this.verifierOptions = verifierOptions;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        context.put(DiagnosticListener.class, diagnostics);
        
        JavacFileManager.preRegister(context);
        
        reporter = Reporter.instance(context);
        verifyAnnotationCompiler = new VerifyAnnotationCompiler(this);
        nameCompiler = NameCompiler.instance(context);
        typeDeclarationCompiler = new TypeDeclarationCompiler(this);
        missingContractsCompiler = new MissingContractsCompiler(this);
        index = JVerifyIndex.instance(context);
        names = Names.instance(context);
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
        
        missingContractsCompiler.addMissingTypeContracts(filesStarts);

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
            var verifyAnnotation = compilationUnit.packge.getAnnotation(Verify.class);
            verifyAnnotationCompiler.processVerifyAnnotation(verifyAnnotation);
            var dafnyDecls = typeDeclarationCompiler.translateTypeDeclaration(relatedDeclaration);
            verifyAnnotationCompiler.shouldVerifies.pop();
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

    public static Symbol.ClassSymbol getClassSymbol(Names names, JCTree.JCExpression valueArgument) {
        if (valueArgument == null) {
            return null;
        }
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess) {
            if (fieldAccess.name.contentEquals(names._class)) {
                return getClassSymbol(names, fieldAccess.selected);
            }
            if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
                 return classSymbol;
            }
        }
        if (valueArgument instanceof JCTree.JCIdent ident &&
                ident.sym instanceof Symbol.ClassSymbol classSymbol) {
                return classSymbol;
        } else {
            throw new JavaViolationException();
        }
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

    public static boolean typeHasSource(JVerifyIndex index, Symbol.TypeSymbol typeSymbol) {
        return index.getTree(typeSymbol) != null;
    }

    private static boolean isEnum(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            return classType.supertype_field != null && 
                    classType.supertype_field.tsym instanceof Symbol.ClassSymbol classSymbol &&
                    classSymbol.fullname.contentEquals("java.lang.Enum");
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
    public boolean isAnnotated(JCTree.JCModifiers modifiers, Class<? extends Annotation> clazz) {
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

    public static boolean isSynthetic(JVerifyIndex index,  JCTree methodNode, Symbol.MethodSymbol methodSymbol) {
        var containerPos = index.getTree(methodSymbol.enclClass()).pos;
        return methodNode.pos == containerPos;
    }

    public static boolean isSynthetic(long flags) {
        return (flags & Flags.SYNTHETIC) != 0;
    }
    
    public static boolean isStatic(JCTree.JCModifiers modifiers) {
        return (modifiers.flags & Flags.STATIC) != 0;
    }

    public static LiteralExpr getReferenceHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, null);
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
            return translatePrimitiveType(type, origin, isNullable, primitiveTypeKind);
        }

        switch (type) {
            case com.sun.tools.javac.code.Type.ArrayType arrayTypeTree -> {
                return translateArrayType(origin, additionalModifiers, arrayTypeTree, nullableSuffix);
            }
            case com.sun.tools.javac.code.Type.IntersectionClassType _ -> {
                return null;
            }
            case com.sun.tools.javac.code.Type.ClassType classType -> {
                return translateClassType(origin, additionalModifiers, classType, isNullable, nullableSuffix);
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
        reportError(origin, "notSupported", "type " + type.getClass().getName());
        return null;
    }

    public void reportError(JCTree tree, String key, Object... args) {
        reporter.reportError(tree, key, args);
    }
    
    public void reportError(IOrigin origin, String key, Object... args) {
        reporter.reportError(origin, key, args);
    }
    private UserDefinedType translateArrayType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ArrayType arrayTypeTree, String nullableSuffix) {
        // TODO: Assuming nullable here means it's not possible to have non-nullable array elements?
        var elemType = translateType(arrayTypeTree.elemtype, origin, additionalModifiers);
        if (elemType == null) {
            // should be unreachable
            throw new IllegalArgumentException("Array type without element type");
        }
        return new UserDefinedType(origin, new NameSegment(origin, "array" + nullableSuffix, List.of(elemType)));
    }

    private NonProxyType translatePrimitiveType(com.sun.tools.javac.code.Type type, IOrigin origin, boolean isNullable, TypeKind primitiveTypeKind) {
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
                reportError(origin, "notSupported", "keyword 'super' in method signature");
            }
            if (superBound instanceof com.sun.tools.javac.code.Type.IntersectionClassType intersectionClassType) {
                // TODO add test
                return translateType(intersectionClassType.getComponents().getFirst(), origin);
            }
            return translateType(superBound, origin);
        }
        return new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OR_VALUE_OBJECT_NAME, null));
    }

    private Type translateClassType(IOrigin origin, 
                                    JCTree.JCModifiers additionalModifiers, 
                                    com.sun.tools.javac.code.Type.ClassType classType, 
                                    boolean isNullable, 
                                    String nullableSuffix) {

        Type remappedType = new ModifiableObjectCompiler(this).getRemappedType(classType, origin, additionalModifiers);
        if (remappedType != null) {
            return remappedType;
        }

        var builtinType = new JVerifyGhostExpressionCompiler(expressionCompiler).translateClassType(classType, origin, isNullable);
        if (builtinType != null) {
            return builtinType;
        }

        if (isRecord(classType) && isNullable) {
            reportError(origin, "notSupported", "nullable record type");
            return null;
        }

        // Remove the name qualification because we do not support that yet
        var compiledName = nameCompiler.getCompiledName(classType.tsym, origin);
        var typeArgumentsStream = classType.getTypeArguments().stream().map(a -> translateType(a, origin, null));
        if (classType.tsym.isDirectlyOrIndirectlyLocal()) {
            var ownerTypes = getOwnAndEnclosedTypeParameters(classType.tsym).toList();
            Stream<Type> typeStream = ownerTypes.stream().map(tp -> translateType(tp.type, toOrigin(tp)));
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
                // TODO needs a test. Something like the following
                // The Map.Entry[]::new introduces a member reference with a 
                // raw type that's not replaced with Object during type resolution
                        /*
                        
            interface IT {}
            record Outer(List<IT> years) {
                public Map<String, IT> asMap(List<String> mapKeys) {
                    var size = 10;
                    Stream<Map.Entry<String, IT>> entries = IntStream.range(0, size).mapToObj(i -> Map.entry(mapKeys.get(i), years.get(i)));
                    return Map.ofEntries(entries.toArray(Map.Entry[]::new));
                }
            }
                        */

                // unresolved raw type
                var objectType = translateType(Symtab.instance(context).objectType, origin);
                typeArguments = IntStream.range(0, classType.tsym.getTypeParameters().size()).
                        mapToObj(_ -> objectType).toList();
            }
        }
        NameSegment nameSegment = new NameSegment(origin, compiledName, typeArguments);
        if (isNullable) {
            nameSegment = new NameSegment(nameSegment.getOrigin(), nameSegment.getName() + nullableSuffix, nameSegment.getOptTypeArguments());
        }
        return new UserDefinedType(origin, nameSegment);
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
        return getName(tree, nameCompiler.getCompiledName(symbol, tree), symbol.name.length());
    }

    public Name getName(JCTree tree, String name) {
        return getName(tree, name, name.length());
    }

    public Name getName(JCTree tree, String name, int length) {
        var positionCalculator = new PositionCalculator(reporter.compilationUnit);
        int startPos = positionCalculator.getStartPos(tree);
        var startToken = positionCalculator.toToken(startPos);
        var endToken = positionCalculator.toToken(startPos + length);
        var origin = startToken == null ? reporter.contextOrigins.peek() : new TokenRangeOrigin(startToken, endToken);
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
        return reporter.toOrigin(node);
    }

    public static TokenRange originToRange(IOrigin tokenRangeOrigin) {
        if (tokenRangeOrigin instanceof SourceOrigin sourceOrigin) {
            return new TokenRange(sourceOrigin.getEntireRange().getStartToken(), sourceOrigin.getEntireRange().getEndToken());
        } else if (tokenRangeOrigin instanceof TokenRangeOrigin trOrigin) {
            return new TokenRange(trOrigin.getStartToken(), trOrigin.getEndToken());
        } else {
            throw new JavaToDafnyCompiler.NotImplementedException(tokenRangeOrigin.getClass().getName());
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

    /// The root Object type is currently defined directly in additional.dfy,
    /// and includes the declaration of the `Object.equals` function.
    /// But many Java types don't explicitly implement `equals`
    /// because it has a default implementation based on reference equality.
    /// Not having a class provide a body for a trait method isn't necessarily a problem in Dafny,
    /// but it IS an error to not at least repeat the bodiless declaration.
    /// Hence, this synthetic declaration we have to add to every constructable class.
    public static Function equalsFunctionDeclaration(IOrigin origin) {
        var otherIn = new Formal(origin, new Name(origin, "obj"), new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OR_VALUE_OBJECT_NAME, null)),
                false, true, null, null, false, false, false, null);
        return new Function(origin, new Name(origin, "equals"), null, false, null, List.of(),
                List.of(otherIn),
                List.of(), List.of(), new Specification<>(List.of(), null),
                new Specification<>(List.of(), null), false, false, null, new BoolType(origin),
                null, null, null);
    }

    public boolean isImmutable(Symbol.ClassSymbol classSymbol) {
        Symtab symtab = Symtab.instance(context);
        if (classSymbol.type == symtab.objectType) {
            return true;
        }
        if (JavaToDafnyCompiler.isInterface(classSymbol) && !isAnnotated(classSymbol.type, Modifiable.class)) {
            return true;
        }
        boolean anonymousImmutableType = isAnonymousOrFinalImmutableType(classSymbol);
        if (anonymousImmutableType) {
            return true;
        }
        return isRecord(classSymbol.type) || isImmutableClass(classSymbol);
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
                if (!isImmutable((Symbol.ClassSymbol) superclass.tsym)) {
                    return false;
                }
            }
            return classSymbol.getInterfaces().stream().
                    allMatch(it -> isImmutable((Symbol.ClassSymbol) it.tsym));
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
                immutableClass = contract.immutable();
            }
        }
        return immutableClass;
    }
}


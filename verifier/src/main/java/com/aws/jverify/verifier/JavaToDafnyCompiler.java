package com.aws.jverify.verifier;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;
import com.aws.jverify.generated.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;


public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = JVerify.class.getName();
    JCTree.JCCompilationUnit compilationUnit;
    Stack<IOrigin> contextOrigins = new Stack<>();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    private JCDiagnostic.Factory diagnosticFactory;

    public @Nullable FilesContainer analyzeJavaCode(Context context, VerifierOptions options, List<JavaFileObject> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);

        if (!Files.exists(options.libraryJar().toAbsolutePath())) {
            throw new IllegalArgumentException("Could not find file: " + options.libraryJar());
        }
        List<String> javacOptions = List.of("-classpath", options.libraryJar().toAbsolutePath().toString());
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                null,
                fileManager,
                diagnostics,
                javacOptions,  // Add classpath here
                null,
                files
        );

        List<FileStart> filesStarts = new ArrayList<>();
        var parsed = task.parse();
        task.analyze();
        this.diagnosticFactory = JCDiagnostic.Factory.instance(context);

        for (var compilationUnit : parsed) {
            this.compilationUnit = (JCTree.JCCompilationUnit) compilationUnit;

            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    JCDiagnostic.DiagnosticPosition position = new JCDiagnostic.DiagnosticPosition() {
                        @Override
                        public JCTree getTree() {
                            return null;
                        }

                        @Override
                        public int getStartPosition() {
                            return (int) diagnostic.getStartPosition();
                        }

                        @Override
                        public int getPreferredPosition() {
                            return (int) diagnostic.getPosition();
                        }

                        @Override
                        public int getEndPosition(EndPosTable endPosTable) {
                            return (int) diagnostic.getEndPosition();
                        }
                    };
                    reportError(position, "javaError", diagnostic.getMessage(Locale.ENGLISH));
                    return null;
                }
            }

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
            filesStarts.add(new FileStart(this.compilationUnit.sourcefile.toUri().toString(), topLevelDecls));
        }

        return new FilesContainer(filesStarts);
    }

    private void reportError(JCTree tree, String key, Object... args) {
        reportError(positionFromNode(tree), key, args);
    }
    private void reportError(JCDiagnostic.DiagnosticPosition position, String key, Object... args) {
        this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                new DiagnosticSource(compilationUnit.getSourceFile(), null), position, key,
                args));
    }

    List<AttributedExpression> invariants = new ArrayList<>();
    
    @Nullable TopLevelDecl translateTypeDeclaration(Tree tree, Stack<Tree> nestedTypes) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            var name = getName(classDecl, classDecl.name);
            var origin = declToOrigin(classDecl, name);
            contextOrigins.push(origin);

            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                    a -> a));
            if (annotationsByName.containsKey(Immutable.class.getSimpleName())) {
                reportError(classDecl, "notSupported", "@ValueType");
            }
            
            TopLevelDecl result;
            if (isEnum(classDecl.type)) {
                result = translateEnum(classDecl, origin, name);
            } 
            else {
                result = translateClass(nestedTypes, classDecl, origin, name);
            }
            contextOrigins.pop();
            return result;
        }
        if (tree instanceof JCTree jcTree) {
            reportError(jcTree, "notSupported", tree.getClass().getSimpleName());
            return null;
        } else {
            throw new NotImplementedException(tree.getClass().getName());
        }
    }

    private ClassDecl translateClass(Stack<Tree> nestedTypes, JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        invariants.clear();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (methodDecl.getModifiers().getAnnotations().stream().
                        anyMatch(a -> a.getAnnotationType() instanceof JCTree.JCIdent ident && 
                                ident.name.contentEquals("Invariant"))) {
                    var invariantName = getName(methodDecl, methodDecl.name);
                    var invariantOrigin = declToOrigin(methodDecl, invariantName);
                    ApplySuffix call = new ApplySuffix(invariantOrigin, new NameSegment(invariantOrigin,
                            methodDecl.name.toString(), null), null, new ActualBindings(List.of()), null);
                    invariants.add(new AttributedExpression(call,null, null)); 
                }
            }
        }

        ArrayList<MemberDecl> members = new ArrayList<>();
        for (var member : classDecl.getMembers()) {
            var dafnyMember = translateMember(member, nestedTypes);
            if (dafnyMember != null) {
                members.add(dafnyMember);
            }
        }
        return new ClassDecl(origin, name, null,
                List.of(), members, List.of(), false);
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

    private Field translateField(JCTree.JCVariableDecl variableDecl) {
        if (variableDecl.getInitializer() != null) {
            throw new RuntimeException("Field initializers are not supported yet");
        }
        Name fieldName = getName(variableDecl, variableDecl.name);
        return new Field(declToOrigin(variableDecl, fieldName), fieldName,
                null,
                false,
                toType(variableDecl.vartype, isNullable(variableDecl.getModifiers())));
    }

    private boolean isNullable(JCTree.JCModifiers modifiers) {
        return modifiers.getAnnotations().stream().anyMatch(
                a -> a.getAnnotationType() instanceof JCTree.JCIdent ident && ident.name.contentEquals("Nullable"));
    }

    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        var name = getName(method, method.name);
        var origin = declToOrigin(method, name);

        var annotations = method.getModifiers().getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                a -> a));
        
        if (annotationsByName.containsKey(InheritContract.class.getSimpleName())) {
            reportError(method, "notSupported", "@InheritContract");
            return null;
        }

        List<Formal> ins = method.getParameters().map(jvd ->
        {
            Name formalName = getName(jvd, jvd.getName());
            return new Formal(declToOrigin(jvd, formalName), formalName, toType(jvd.getType()), false, true,
                    null, null, false, false, false, null);
        });
        var isStatic = (method.getModifiers().flags & Flags.STATIC) == Flags.STATIC;

        if (annotationsByName.containsKey(Pure.class.getSimpleName())) {
            var header = new HeaderContainer();
            var postHeader = translateHeader(method.body.stats, header);
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
            if (statement instanceof JCTree.JCReturn returnStatement) {
                var body = toExpr(returnStatement.expr);
                return new Function(origin, name, null, false, null, List.of(),
                        ins, header.preconditions, header.postconditions, header.getReads(),
                        header.getDecreases(), isStatic, false, null, returnType,
                        body, null, null);
            } else {
                reportError(method, "pureMethodNeedsReturnStatement");
                return null;
            }
        } else {
            var header = new HeaderContainer();
            var postHeader = translateHeader(method.getBody().stats, header);
            applyInvariants(method, header);
            checkEmptyExpressions(method, header.invariants, "invariants", "method");

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

            var bodyStatements = translateStatements(postHeader);
            if (method.name.contentEquals("<init>")) {
                return new Constructor(origin, new Name(origin, "_ctor"), null, false, null, List.of(), ins,
                        header.preconditions, header.postconditions, header.getReads(), 
                        header.getDecreases(), header.getModifies(),
                        new DividedBlockStmt(toOrigin(method.body), null, bodyStatements, null, List.of()));
            } else if (annotationsByName.containsKey(Proof.class.getSimpleName())) {
                return new Method(origin, name, null, false, null, List.of(),
                        ins, header.preconditions, header.postconditions, header.getReads(),
                        header.getDecreases(), header.getModifies(), 
                        isStatic, outs,
                        new BlockStmt(toOrigin(method.body), null, bodyStatements), false);
            } else {
                return new Method(origin, name, null, false, null, List.of(),
                        ins, header.preconditions, header.postconditions, header.getReads(),
                        header.getDecreases(), header.getModifies(), isStatic, outs,
                        new BlockStmt(toOrigin(method.body), null, bodyStatements), false);
            }
        }
    }
    
    private JCDiagnostic.DiagnosticPosition positionFromNode(JCTree node) {
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
                if (!method.name.contentEquals("<init>")) {
                    header.preconditions.add(invariant);
                }
                header.postconditions.add(invariant);
            }
        }
    }

    private Expression toExpr(JCTree tree) {
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

    private AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr) {
        var origin = toOrigin(expr);
        if (expr instanceof JCTree.JCNewClass newClass) {
            var argBindings = newClass.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
            return new AllocateClass(origin, null, toType(newClass.clazz), new ActualBindings(argBindings));
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
        } else if (expr instanceof JCTree.JCUnary unary) {
            var innerExpr = toExpr(unary.getExpression());
            if (unary.getOperator().name.toString().equals("!")) {
                return new UnaryOpExpr(origin, innerExpr, UnaryOpExprOpcode.Not);
            } else {
                reportError(unary, "notSupported", "unary operator" + unary.getOperator());
                return getHole(origin);
            }
        } else if (expr instanceof JCTree.JCBinary binary) {
            var left = toExpr(binary.getLeftOperand());
            var right = toExpr(binary.getRightOperand());
            BinaryExprOpcode dafnyOperator = toDafny(binary.getOperator());
            if (dafnyOperator == null) {
                reportError(binary, "notSupported", "operator" + binary.getOperator());
                return getHole(origin);
            }
            return new BinaryExpr(origin, dafnyOperator, left, right);
        } else if (expr instanceof JCTree.JCIdent identifier) {
            if (identifier.name.contentEquals("this")) {
                return new ThisExpr(origin);
            }
            return new NameSegment(origin, identifier.getName().toString(), null);
        } else if (expr instanceof JCTree.JCLiteral literal) {
            if (literal.typetag == TypeTag.BOOLEAN) {
                return new LiteralExpr(toOrigin(literal), literal.value != (Object) 0);
            }
            return new LiteralExpr(origin, literal.value);
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
            if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
                return new MemberSelectExpr(origin, selectedExpr, new Name(origin, "Length"));
            }
            
            if (isEnum(fieldAccess.selected)) {
                return new ApplySuffix(origin, new NameSegment(origin, fieldAccess.name.toString(), null), 
                        null, new ActualBindings(List.of()), null);
            } else {
                return new ExprDotName(origin, toExpr(fieldAccess.selected), getName(fieldAccess, fieldAccess.name), null);
            }
        } else if (expr instanceof JCTree.JCArrayAccess arrayAccess) {
            var arrayExpr = toExpr(arrayAccess.getExpression());
            var indexExpr = toExpr(arrayAccess.getIndex());
            return new SeqSelectExpr(origin, true, arrayExpr, indexExpr, null, null);
        } else if (expr instanceof JCTree.JCParens parens) {
            return toExpr(parens.getExpression());
        }
        reportError(expr, "notSupported", expr.getClass().getSimpleName());
        return getHole(origin);  
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
     * must be translated by {@link #translateStatement(JCTree.JCStatement)},
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
            default -> null;
        };
    }

    private @Nullable Type toType(JCTree tree) {
        return toType(tree, false, null);
    }

    private @Nullable Type toType(JCTree tree, boolean isNullable) {
        return toType(tree, isNullable, null);
    }

    private @Nullable Type toType(JCTree tree, boolean isNullable, @Nullable IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> toOrigin(tree));
        
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
                }
                case CHAR -> {
                }
                case FLOAT -> {
                }
                case DOUBLE -> {
                }
            }


            reportError(tree, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
            return null;
        } else if (tree instanceof JCTree.JCArrayTypeTree arrayTypeTree) {
            var elemType = toType(arrayTypeTree.getType(), false, originOverride);
            if (elemType == null) {
                // should be unreachable
                throw new IllegalArgumentException("Array type without element type");
            }
            return new UserDefinedType(origin, new NameSegment(origin, "array", List.of(elemType)));
        } else if (tree instanceof JCTree.JCIdent identifier) {
            var nullableSuffix = isNullable ? "?" : "";
            return new UserDefinedType(origin, new NameSegment(origin, identifier.getName().toString() + nullableSuffix, List.of()));
        }

        reportError(tree, "notSupported", tree.getClass().getSimpleName());
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
        int startPos = getStartPos(tree);
        var startToken = toToken(startPos);
        var endToken = toToken(startPos + name.length());
        var origin = startToken == null ? contextOrigins.peek() : new TokenRangeOrigin(startToken, endToken);
        return new Name(origin, name.toString());
    }

    private IOrigin declToOrigin(JCTree node, Name name) {
        var entireRange = toOrigin(node);
        return new SourceOrigin(originToRange(entireRange), originToRange(name.getOrigin()));
    }
    
    private IOrigin toOrigin(JCTree node) {
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
            pos = getEndPos(methodDecl.mods);
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
            pos = getEndPos(classDecl.mods);
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

    private List<Statement> translateStatements(List<JCTree.JCStatement> statements) {
        return statements.stream().map(this::translateStatement).filter(Objects::nonNull).toList();
    }

    private @Nullable Statement translateStatement(JCTree.JCStatement statement) {
        var origin = toOrigin(statement);
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            if (expr instanceof JCTree.JCMethodInvocation invocation) {
                var jverifyMethod = getJVerifyMethod(invocation);
                if (jverifyMethod != null) {
                    var name = jverifyMethod.getQualifiedName().toString();
                    if (name.equals("check")) {
                        if (invocation.args.size() != 1) {
                            throw new JavaViolationException("Check should have a single argument");
                        }
                        return new AssertStmt(toOrigin(invocation), null,
                                toExpr(invocation.args.getFirst()), null);
                    } else {
                        reportError(invocation, "contractAfterBody", jverifyMethod.getQualifiedName());
                        return null;
                    }
                } else {
                    if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name.contentEquals("super")) {
                        if (!invocation.getArguments().isEmpty()) {
                            reportError(invocation, "notSupported", "super calls with arguments");
                            return null;
                        }
                        return null;
                    }
                    var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();
                    ApplySuffix applySuffix = new ApplySuffix(toOrigin(invocation), toExpr(invocation.getMethodSelect()), null,
                            new ActualBindings(argBindings), null);
                    return new AssignStatement(origin, null, List.of(),
                            List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);
                }
            }
            if (expressionStatement.getExpression() instanceof JCTree.JCAssign assign) {
                List<Expression> lhss = List.of(toExpr(assign.getVariable()));
                List<AssignmentRhs> rhss = List.of(toAssignmentRhs(assign.getExpression()));
                return new AssignStatement(toOrigin(assign), null, lhss, rhss, false);
            }
        } else if (statement instanceof JCTree.JCAssert assertStmt) {
            return new AssertStmt(origin, null,
                    toExpr(assertStmt.getCondition()), null);
        } else if (statement instanceof JCTree.JCIf ifStatement) {
            var condition = toExpr(ifStatement.getCondition());
            var thenBranch = blockifyStatement(translateStatement(ifStatement.getThenStatement()));
            BlockStmt elseBranch = null;
            if (ifStatement.getElseStatement() != null) {
                elseBranch = blockifyStatement(translateStatement(ifStatement.getElseStatement()));
            }
            return new IfStmt(origin, null, false, condition,
                    thenBranch, elseBranch);
        } else if (statement instanceof JCTree.JCBlock blockStatement) {
            return new BlockStmt(origin, null,
                    blockStatement.getStatements().map(this::translateStatement).stream().toList());
        } else if (statement instanceof JCTree.JCReturn returnStatement) {
            var expr = returnStatement.getExpression();
            if (expr == null) {
                return new ReturnStmt(origin, null, null);
            } else {
                return new ReturnStmt(origin, null,
                        List.of(new ExprRhs(toOrigin(expr), null, toExpr(expr))));
            }
        } else if (statement instanceof JCTree.JCVariableDecl variableDecl) {
            LocalVariable localVariable = new LocalVariable(origin,
                    variableDecl.getName().toString(), toType(variableDecl.getType(), false, origin), false);
            ConcreteAssignStatement initializer = null;
            if (variableDecl.getInitializer() != null) {
                var e = toExpr(variableDecl.getInitializer());
                List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
                List<AssignmentRhs> rhss = List.of(new ExprRhs(e.getOrigin(), null, e));
                initializer = new AssignStatement(e.getOrigin(), null, lhss, rhss, false);
            }

            return new VarDeclStmt(origin, null, List.of(localVariable), initializer);
        } else if (statement instanceof JCTree.JCWhileLoop whileLoop) {
            var header = new HeaderContainer();
            var postHeader = translateHeader(whileLoop.body, header);

            checkEmptyExpressions(whileLoop, header.preconditions, "preconditions", "loop");
            checkEmptyExpressions(whileLoop, header.postconditions, "postconditions", "loop");

            var bodyStatements = translateStatements(postHeader);
            var condition = toExpr(whileLoop.getCondition());
            return new WhileStmt(origin, null, header.invariants, new Specification<>(header.decreases, null),
                    new Specification<>(header.modifies, null), new BlockStmt(origin, null, bodyStatements),
                    condition);
        }
        reportError(statement, "notSupported", statement.getClass().getSimpleName());
        return null;
    }

    private void checkEmptyExpressions(JCTree tree, 
                                       List<AttributedExpression> expressions,
                                       String typeName,
                                       String containerName) {
        for (var _ : expressions) {
            reportError(tree, "wrongContract", typeName, containerName);
        }
    }

    private static class HeaderContainer {
        List<AttributedExpression> preconditions;
        List<AttributedExpression> postconditions;
        List<Name> returnNames;
        List<AttributedExpression> invariants;
        List<Expression> decreases;
        List<FrameExpression> reads;
        List<FrameExpression> modifies;

        HeaderContainer() {
            preconditions = new ArrayList<>();
            postconditions = new ArrayList<>();
            returnNames = new ArrayList<>();
            invariants = new ArrayList<>();
            decreases = new ArrayList<>();
            reads = new ArrayList<>();
            modifies = new ArrayList<>();
        }

        public Specification<FrameExpression> getReads() {
            return new Specification<>(reads, null);
        }

        public Specification<FrameExpression> getModifies() {
            return new Specification<>(modifies, null);
        }

        public Specification<Expression> getDecreases() {
            return new Specification<>(decreases, null);
        }
    }

    /**
     * @see #translateHeader(List, HeaderContainer)
     */
    private List<JCTree.JCStatement> translateHeader(JCTree.JCStatement statement, HeaderContainer header) {
        var statements = statement instanceof JCTree.JCBlock block
                ? block.getStatements()
                : List.of(statement);
        return translateHeader(statements, header);
    }

    /**
     * Translates header statements from the start of {@code statements}
     * until the first non-header statement or the end of the list,
     * appending the translations to the given {@link HeaderContainer},
     * and returning a list view of the remaining statements.
     *
     * <p>NOTE: The list view is constructed using {@link List#subList(int, int)} and has the corresponding caveats;
     * namely, that it is backed by the original list.
     */
    private List<JCTree.JCStatement> translateHeader(List<JCTree.JCStatement> statements, HeaderContainer header) {
        var headerStatements = 0;
        statementLoop: for (var statement : statements) {
            if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                    && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
                break;
            }
            var jverifyMethod = getJVerifyMethod(invocation);
            if (jverifyMethod == null) {
                break;
            }
            var methodName = jverifyMethod.getQualifiedName().toString();
            switch (methodName) {
                case "check" -> {
                    // not a header method, so stop here
                    break statementLoop;
                }
                case Common.PRECONDITION -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A precondition call may have only one argument");
                    }
                    header.preconditions.add(new AttributedExpression(toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "postcondition" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("An postcondition call may have only one argument");
                    }
                    var first = invocation.getArguments().getFirst();
                    if (first instanceof JCTree.JCLambda lambda) {
                        if (lambda.getParameters().size() != 1) {
                            throw new JavaViolationException("An ensures call lambda may take only one argument");
                        }
                        var parameter = lambda.getParameters().getFirst();
                        header.returnNames.add(new Name(toOrigin(lambda), parameter.getName().toString()));
                        var postconditionPredicate = toExpr(lambda.getBody());
                        if (postconditionPredicate != null) {
                            header.postconditions.add(new AttributedExpression(postconditionPredicate, null, null));
                        }
                    } else {
                        var dafnyExpr = toExpr(first);
                        header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
                    }
                }
                case "invariant" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("invariant should have a single argument");
                    }
                    header.invariants.add(new AttributedExpression(toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "decreases" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("decreases should have a single argument");
                    }
                    reportError(invocation, "notSupported", "decreases");
                    return null;
                }
                case "reads" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A reads call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = toOrigin(origExpr);
                    var expr = toExpr(origExpr);
                    header.reads.add(new FrameExpression(origin, expr, null));
                }
                case "modifies" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A modifies call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = toOrigin(origExpr);
                    var expr = toExpr(origExpr);
                    header.modifies.add(new FrameExpression(origin, expr, null));
                }
                default -> {
                    reportError(invocation, "notSupported", methodName);
                    return null;
                }
            }
            headerStatements++;
        }
        return statements.subList(headerStatements, statements.size());
    }

    /**
     * Returns the specified statement as-is if it's already a {@link BlockStmt},
     * or wraps it in a singleton block otherwise.
     */
    private static BlockStmt blockifyStatement(Statement statement) {
        return statement instanceof BlockStmt block
                ? block
                : new BlockStmt(statement.getOrigin(), null, List.of(statement));
    }

    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.outermostClass().className().contentEquals(JVERIFY_CLASS);
    }

    /**
     * If the specified invocation's method is from the JVerify library,
     * returns its {@link Symbol.MethodSymbol}.
     * Otherwise, returns {@code null}.
     */
    private static Symbol.MethodSymbol getJVerifyMethod(JCTree.JCMethodInvocation invocation) {
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        return fromJVerify(methodSymbol) ? methodSymbol : null;
    }
}

/**
 * Occurs when the contracts that we expect from Java resolution are violated
 * Indicates a JVerify compiler bug
 */
class JavaViolationException extends RuntimeException {
    public JavaViolationException(String message) {
        super(message);
    }
}
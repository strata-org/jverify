package com.aws.jverify.verifier;

import com.aws.jverify.*;

import com.aws.jverify.common.Common;
import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.code.Symbol;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;
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
    private Symbol.@Nullable ClassSymbol contractClass;
    private int generatedIndex = 0; 

    public JavaToDafnyCompiler() {
        shouldVerifies.push(ShouldVerifyMode.DefaultYes);
    }
    
    public @Nullable FilesContainer analyzeJavaCode(Context context, VerifierOptions options, List<JavaFileObject> files) {        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);

        if (!Files.exists(options.libraryJar().toAbsolutePath())) {
            throw new IllegalArgumentException("Could not find file: " + options.libraryJar());
        }

        files.add(new SourceFile("builtin-contracts.java", Common.getResourceFile(getClass(), builtinFile)));
                
        List<String> javacOptions = List.of("-classpath", options.libraryJar().toAbsolutePath().toString());
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                null,
                fileManager,
                diagnostics,
                javacOptions,
                null,
                files
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
    
    private final Set<Symbol.ClassSymbol> classWithExternalContract = new HashSet<>();
    private void findExternalContracts(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        for (var typeDecl : compilationUnit.getTypeDecls()) {
            if (typeDecl instanceof JCTree.JCClassDecl classDecl) {
                var annotations = classDecl.getModifiers().getAnnotations();
                var annotationsByName = annotations.stream().collect(Collectors.toMap(
                        (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                        a -> a));
                var contractAnnotation = annotationsByName.get(Contract.class.getName());
                if (contractAnnotation != null) {
                    var arguments = getArguments(contractAnnotation);
                    Symbol.ClassSymbol classSymbol = getClassSymbol(arguments.get("value"));
                    if (!classWithExternalContract.add(classSymbol)) {
                        reportError(contractAnnotation, "duplicateContract", classDecl.name);
                    }
                }
            }
        }
    }

    private static Symbol.ClassSymbol getClassSymbol(JCTree.JCExpression valueArgument) {
        Symbol.ClassSymbol classSymbol;
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess &&
            fieldAccess.selected instanceof JCTree.JCIdent ident &&
            ident.sym instanceof Symbol.ClassSymbol classSymbol2)
        {
            classSymbol = classSymbol2;
        } else {
            throw new JavaViolationException();
        }
        return classSymbol;
    }

    private FileStart translateFile(JCTree.JCCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;

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
        return new FileStart(this.compilationUnit.sourcefile.toUri().toString(), topLevelDecls);
    }

    private void reportError(IOrigin origin, String key, Object... args) {
        reportError(positionFromOrigin(origin), key, args);
    }

    private JCDiagnostic.DiagnosticPosition positionFromOrigin(IOrigin origin) {
        return new DiagnosticPositionFromOrigin(originToRange(origin), compilationUnit.lineMap);
    }

    private void reportError(JCTree tree, String key, Object... args) {
        reportError(positionFromNode(tree, compilationUnit), key, args);
    }
    
    private void reportError(JCDiagnostic.DiagnosticPosition position, String key, Object... args) {
        this.diagnostics.report(diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                new DiagnosticSource(compilationUnit.getSourceFile(), null), position, key,
                args));
    }

    List<AttributedExpression> invariants = new ArrayList<>();
    
    enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }
    private final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();
    private boolean shouldVerify() {
        if (contractClass != null) {
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
    
    @Nullable TopLevelDecl translateTypeDeclaration(Tree tree, Stack<Tree> nestedTypes) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            var annotations = classDecl.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));

            processVerifyAnnotation(annotationsByName);

            Name name = getName(classDecl, classDecl.name);
            if (classWithExternalContract.contains(classDecl.sym)) {
                if (shouldVerify()) {
                    reportError(name.getOrigin(), "verifiedTypeWithExternalContract", classDecl.name);
                }
                return null;
            }
            var origin = declToOrigin(classDecl, name);
            contextOrigins.push(origin);

            var contractAnnotation = annotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                if (isNestedClass(classDecl)) {
                    reportError(contractAnnotation, "nestedContractClass", classDecl.name);
                }
                var arguments = getArguments(contractAnnotation);
                contractClass = getClassSymbol(arguments.get("value"));
                name = new Name(name.getOrigin(), contractClass.name.toString());
            }
            if (annotationsByName.containsKey(Immutable.class.getName())) {
                reportError(classDecl, "notSupported", "@ValueType");
            }
            
            TopLevelDecl result;
            if (isEnum(classDecl.type)) {
                result = translateEnum(classDecl, origin, name);
            } 
            else {
                result = translateClass(nestedTypes, classDecl, origin, name);
            }
            contractClass = null;
            contextOrigins.pop();
            shouldVerifies.pop();
            return result;
        }
        if (tree instanceof JCTree jcTree) {
            reportError(jcTree, "notSupported", tree.getClass().getSimpleName());
            return null;
        } else {
            throw new NotImplementedException(tree.getClass().getName());
        }
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
        return new ClassDecl(origin, name, null, List.of(), members, List.of(), false);
    }
    
    static final String builtinFile = "/builtin-contracts.java";
    private boolean isAlreadyVerified() {
        return compilationUnit.getSourceFile().getName().equals(builtinFile);
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

    private @Nullable Field translateField(JCTree.JCVariableDecl variableDecl) {
        Name fieldName = getName(variableDecl, variableDecl.name);
        IOrigin origin = declToOrigin(variableDecl, fieldName);
        Type type = toType(variableDecl.vartype, isNullable(variableDecl.getModifiers()));
        if (variableDecl.getInitializer() != null) {
            var isFinal = (variableDecl.mods.flags & Flags.FINAL) != 0;
            if (isFinal) {
                var rhs = toExpr(variableDecl.getInitializer());
                var isStatic = (variableDecl.mods.flags & Flags.STATIC) != 0;
                return new ConstantField(origin, fieldName, getAttributes(origin), false, type, rhs, isStatic, false);
            } else {
                reportError(variableDecl, "notSupported", "Field initializers");
                return null;
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

    private @Nullable MethodOrFunction translateMethodDecl(JCTree.JCMethodDecl method) {
        var name = getName(method, method.name);
        var origin = declToOrigin(method, name);


        var annotations = method.getModifiers().getAnnotations();
        var annotationsByName = annotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a));
        
        processVerifyAnnotation(annotationsByName);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        
        if (annotationsByName.containsKey(InheritContract.class.getName())) {
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

        if (annotationsByName.containsKey(Pure.class.getName())) {
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
                var body = shouldVerify ? toExpr(returnStatement.expr) : null;
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

            if (method.name.contentEquals("<init>")) {
                DividedBlockStmt body;
                if (shouldVerify) {
                    var bodyStatements = translateStatements(postHeader);
                    body = new DividedBlockStmt(toOrigin(method.body), null, List.of(), bodyStatements, null, List.of());
                } else {
                    body = null;
                }
                return new Constructor(origin, new Name(origin, "_ctor"), null, false, null, List.of(), ins,
                        header.preconditions, header.postconditions, header.getReads(), 
                        header.getDecreases(), header.getModifies(),
                        body);
            } else {
                BlockStmt body;
                if (shouldVerify) {
                    var bodyStatements = translateStatements(postHeader);
                    body = new BlockStmt(toOrigin(method.body), null, List.of(), bodyStatements);
                } else {
                    body = null;
                }
                if (annotationsByName.containsKey(Proof.class.getName())) {
                    return new Method(origin, name, null, false, null, List.of(),
                            ins, header.preconditions, header.postconditions, header.getReads(),
                            header.getDecreases(), header.getModifies(), 
                            isStatic, outs,
                            body, false);
                } else {
                    return new Method(origin, name, null, false, null, List.of(),
                            ins, header.preconditions, header.postconditions, header.getReads(),
                            header.getDecreases(), header.getModifies(), isStatic, outs,
                            body, false);
                }
            }
        }
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
            switch(unary.getTag()) {
                case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                    reportError(expr, "mutatingExpression", unary.getOperator().name.toString());
                    return getHole(origin);
                }
                case JCTree.Tag.NOT -> {
                    return new UnaryOpExpr(origin, innerExpr, UnaryOpExprOpcode.Not);
                }
                case JCTree.Tag.NEG -> {
                    return new BinaryExpr(origin, BinaryExprOpcode.Mul, innerExpr, new LiteralExpr(origin, -1));
                }
                case JCTree.Tag.POS -> {
                    return innerExpr;
                }
                default -> {
                    reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return getHole(origin);
                }
            }
        } else if (expr instanceof JCTree.JCBinary binary) {
            var left = toExpr(binary.getLeftOperand());
            var right = toExpr(binary.getRightOperand());
            Symbol.OperatorSymbol operator = binary.getOperator();
            return translateBinary(binary, binary.type, binary.getLeftOperand().type, operator, left, right);
        } else if (expr instanceof JCTree.JCIdent identifier) {
            if (identifier.name.contentEquals("this")) {
                return new ThisExpr(origin);
            }
            return new NameSegment(origin, identifier.getName().toString(), null);
        } else if (expr instanceof JCTree.JCLiteral literal) {
            if (literal.typetag == TypeTag.BOOLEAN) {
                return new LiteralExpr(toOrigin(literal), literal.getValue());
            }
            if (literal.typetag == TypeTag.CHAR) {
                return new CharLiteralExpr(toOrigin(literal), literal.getValue());
            }
            return new LiteralExpr(origin, literal.getValue());
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
        } else if (expr instanceof JCTree.JCAssignOp assignOp) {
            reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
            return getHole(origin);
        }
        reportError(expr, "notSupported", expr.getClass().getSimpleName());
        return getHole(origin);  
    }

    private Expression translateBinary(JCTree node,
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
            case "&" -> BinaryExprOpcode.BitwiseAnd;
            case "|" -> BinaryExprOpcode.BitwiseOr;
            case "^" -> BinaryExprOpcode.BitwiseXor;
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


            reportError(tree, "notSupported", "Primitive type kind %s".formatted(primitiveTypeKind));
            return null;
        } else if (tree instanceof JCTree.JCArrayTypeTree arrayTypeTree) {
            var elemType = toType(arrayTypeTree.getType(), false, originOverride);
            if (elemType == null) {
                // should be unreachable
                throw new IllegalArgumentException("Array type without element type");
            }
            return new UserDefinedType(origin, new NameSegment(origin, "array", List.of(elemType)));
        } else if (tree instanceof JCTree.JCExpression expr) {
            var expression = toExpr(expr);
            
            // Remove the name qualification because we do not support that yet
            Expression nameSegment;
            if (expression instanceof ExprDotName exprDotName) {
                nameSegment = new NameSegment(exprDotName.getOrigin(), exprDotName.getSuffixNameNode().getValue(), null);
            } else {
                nameSegment = expression;
            }
            if (isNullable && nameSegment instanceof NameSegment ns) {
                nameSegment = new NameSegment(ns.getOrigin(), ns.getName() + "?", ns.getOptTypeArguments());
            }
            return new UserDefinedType(origin, nameSegment);
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
        return getName(tree, name.toString());
    }
    
    Name getName(JCTree tree, String name) {
        int startPos = getStartPos(tree);
        var startToken = toToken(startPos);
        var endToken = toToken(startPos + name.length());
        var origin = startToken == null ? contextOrigins.peek() : new TokenRangeOrigin(startToken, endToken);
        return new Name(origin, name);
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

    private <T extends JCTree.JCStatement> List<Statement> translateStatements(List<T> statements) {
        return statements.stream().map(this::translateStatement).filter(Objects::nonNull).toList();
    }

    Queue<Label> labels = new LinkedList<>();
    JCTree.JCStatement outerLoop;
    Map<String, JCTree.JCStatement> labelToLoop = new HashMap<>();
    Map<JCTree.JCStatement, String> forLoopContinueLabels = new HashMap<>();
    Set<JCTree.JCStatement> loopsWithContinue = new HashSet<>();
    private @Nullable Statement translateStatement(JCTree.JCStatement statement) {
        var origin = toOrigin(statement);
        if (statement instanceof JCTree.JCLabeledStatement labeledStatement) {
            labels.add(new Label(origin, labeledStatement.getLabel().toString()));
            return translateStatement(labeledStatement.getStatement());
        }
        var labels = this.labels.stream().toList();
        this.labels.clear();

        switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement -> {
                return translateExpressionStatement(expressionStatement);
            }
            case JCTree.JCAssert assertStmt -> {
                return new AssertStmt(origin, null,
                        toExpr(assertStmt.getCondition()), null);
            }
            case JCTree.JCIf ifStatement -> {
                var condition = toExpr(ifStatement.getCondition());
                var thenBranch = blockifyStatement(translateStatement(ifStatement.getThenStatement()));
                BlockStmt elseBranch = null;
                if (ifStatement.getElseStatement() != null) {
                    elseBranch = blockifyStatement(translateStatement(ifStatement.getElseStatement()));
                }
                return new IfStmt(origin, null, false, condition,
                        thenBranch, elseBranch);
            }
            case JCTree.JCBlock blockStatement -> {
                return new BlockStmt(origin, null, List.of(),
                        blockStatement.getStatements().map(this::translateStatement).stream().toList());
            }
            case JCTree.JCReturn returnStatement -> {
                var expr = returnStatement.getExpression();
                if (expr == null) {
                    return new ReturnStmt(origin, null, null);
                } else {
                    return new ReturnStmt(origin, null,
                            List.of(new ExprRhs(toOrigin(expr), null, toExpr(expr))));
                }
            }
            case JCTree.JCVariableDecl variableDecl -> {
                LocalVariable localVariable = new LocalVariable(origin,
                        variableDecl.getName().toString(), toType(variableDecl.getType(), false, origin), false);
                ConcreteAssignStatement initializer = null;
                if (variableDecl.getInitializer() != null) {
                    var rhs = toAssignmentRhs(variableDecl.getInitializer());
                    List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
                    List<AssignmentRhs> rhss = List.of(rhs);
                    initializer = new AssignStatement(rhs.getOrigin(), null, lhss, rhss, false);
                }

                return new VarDeclStmt(origin, null, List.of(localVariable), initializer);
            }
            case JCTree.JCWhileLoop whileLoop -> {
                return translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x);
            }
            case JCTree.JCForLoop forLoop -> {
                var loop = translateLoop(forLoop, forLoop.getCondition(), forLoop.body, labels, bodyStatements -> {
                    List<Statement> outerBody;
                    List<Statement> steps = translateStatements(forLoop.step);
                    if (loopsWithContinue.contains(forLoop)) {
                        var continueLabel = getForLoopContinueLabel(forLoop);
                        var wrappedBody = new BlockStmt(origin, null, List.of(new Label(origin, continueLabel)), bodyStatements);
                        outerBody = new ArrayList<>(1 + steps.size());
                        outerBody.add(wrappedBody);
                        outerBody.addAll(steps);
                    } else {
                        outerBody = new ArrayList<>(bodyStatements.size() + steps.size());
                        outerBody.addAll(bodyStatements);
                        outerBody.addAll(steps);
                    }
                    return outerBody;
                });
                
                var initializer = translateStatements(forLoop.getInitializer());
                var result = new ArrayList<Statement>(initializer.size() + 1);
                result.addAll(initializer);
                result.add(loop);
                return new BlockStmt(origin, null, List.of(), result);
            }
            
            case JCTree.JCContinue jcContinue -> {
                Name targetLabel = null;
                int breakAndContinueCount = 0;
                var isContinue = true;

                if (jcContinue.label == null) {
                    if (outerLoop == null) {
                        throw new JavaViolationException();
                    } else {
                        loopsWithContinue.add(outerLoop);
                        if (outerLoop instanceof JCTree.JCForLoop forLoop) {
                            var label = getForLoopContinueLabel(forLoop);
                            targetLabel = getName(jcContinue, label);
                            isContinue = false;
                        } else {
                            breakAndContinueCount++;
                        }
                    }
                } else {
                    var loop = this.labelToLoop.get(jcContinue.label.toString());
                    loopsWithContinue.add(loop);
                    if (loop instanceof JCTree.JCForLoop forLoop) {
                        var label = getForLoopContinueLabel(forLoop);
                        targetLabel = getName(jcContinue, label);
                        isContinue = false;
                    } else {
                        targetLabel = getName(jcContinue, jcContinue.label);
                    }
                }
                return new BreakOrContinueStmt(origin, null, targetLabel, breakAndContinueCount, isContinue);
            }
            case JCTree.JCBreak jcBreak -> {
                Name targetLabel = null;
                int breakAndContinueCount = 0;
                if (jcBreak.label == null) {
                    breakAndContinueCount++;
                } else {
                    targetLabel = getName(jcBreak, jcBreak.label);
                }
                return new BreakOrContinueStmt(origin, null, targetLabel, breakAndContinueCount, false);
            }
            case JCTree.JCSkip skip -> {
                return null;
            }
            case null, default -> {
            }
        }
        reportError(statement, "notSupported", statement.getClass().getSimpleName());
        return null;
    }

    private WhileStmt translateLoop(JCTree.JCStatement loop, 
                                    JCTree.JCExpression condition, 
                                    JCTree.JCStatement body,
                                    List<Label> labels,
                                    java.util.function.Function<List<Statement>, List<Statement>> transformBody) {
        var origin = toOrigin(loop);
        var header = new HeaderContainer();
        var postHeader = translateHeader(body, header);

        checkEmptyExpressions(loop, header.preconditions, "preconditions", "loop");
        checkEmptyExpressions(loop, header.postconditions, "postconditions", "loop");

        outerLoop = loop;
        for(var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }

        var dafnyCondition = toExpr(condition);
        var bodyStatements = translateStatements(postHeader);
        var newBodyStatements = transformBody.apply(bodyStatements);
        return new WhileStmt(origin, null, labels, header.invariants, new Specification<>(header.decreases, null),
                new Specification<>(header.modifies, null), new BlockStmt(origin, null, List.of(), newBodyStatements),
                dafnyCondition);
    }

    private String getForLoopContinueLabel(JCTree.JCForLoop forLoop) {
        return forLoopContinueLabels.computeIfAbsent(forLoop, shouldVerifies -> "generated" + generatedIndex++);
    }
    
    private @Nullable Statement translateExpressionStatement(JCTree.JCExpressionStatement statement) {
        var expr = statement.getExpression();
        var origin = toOrigin(expr);
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
        if (expr instanceof JCTree.JCAssign assign) {
            List<Expression> lhss = List.of(toExpr(assign.getVariable()));
            List<AssignmentRhs> rhss = List.of(toAssignmentRhs(assign.getExpression()));
            return new AssignStatement(toOrigin(assign), null, lhss, rhss, false);
        }
        if (expr instanceof JCTree.JCAssignOp assignOp) {
            Expression target = toExpr(assignOp.getVariable());
            List<Expression> lhss = List.of(target);
            var operated = translateBinary(assignOp, assignOp.type, assignOp.getVariable().type, assignOp.getOperator(), 
                    target, toExpr(assignOp.getExpression()));
            List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
            return new AssignStatement(origin, null, lhss, rhss, false);
        }
        if (expr instanceof JCTree.JCUnary unary) {
            JCTree.Tag tag = unary.getTag();
            switch(tag) {
                case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                    if (unary.type.getTag() == TypeTag.FLOAT || unary.type.getTag() == TypeTag.DOUBLE) {
                        reportError(unary, "notSupported", "operator " + unary.getOperator());
                        return null;
                    } else {
                        Expression target = toExpr(unary.getExpression());
                        List<Expression> lhss = List.of(target);
                        
                        var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                                ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;
                        var incremented = new BinaryExpr(origin, opCode, target, new LiteralExpr(origin, 1));
                        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, incremented));
                        return new AssignStatement(origin, null, lhss, rhss, false);
                    }
                    
                }
            }
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
                    header.decreases.add(toExpr(invocation.getArguments().getFirst()));
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
                : new BlockStmt(statement.getOrigin(), null, List.of(), List.of(statement));
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
    public JavaViolationException() {
        super();
    }
    
    public JavaViolationException(String message) {
        super(message);
    }
}
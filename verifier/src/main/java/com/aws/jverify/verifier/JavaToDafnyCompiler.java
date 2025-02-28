package com.aws.jverify.verifier;

import com.aws.jverify.Nat;
import com.aws.jverify.Unbounded;

import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Position;
import com.aws.jverify.generated.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.TypeKind;
import javax.swing.*;
import javax.tools.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.function.Function;

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = "com.aws.jverify.JVerify";
    JCTree.JCCompilationUnit compilationUnit;

    public FilesContainer analyzeJavaCode(VerifierOptions options,  List<JavaFileObject> files) {
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Context context = new Context();
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
        
        for (var compilationUnit : parsed) {
            this.compilationUnit = (JCTree.JCCompilationUnit)compilationUnit;

            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new RuntimeException("Java file had errors: " + diagnostic.getMessage(Locale.ENGLISH));
                }
            }

            ArrayList<TopLevelDecl> topLevelDecls = new ArrayList<>();
            for(var typeDecl : compilationUnit.getTypeDecls()) {
                topLevelDecls.add(translateTypeDeclaration(typeDecl));
            }
            filesStarts.add(new FileStart(this.compilationUnit.sourcefile.toUri().toString(), topLevelDecls));
        }
        
        return new FilesContainer(filesStarts);
    }
    
    TopLevelDecl translateTypeDeclaration(Tree tree) {
        if (tree instanceof JCTree.JCClassDecl classDecl) {
            ArrayList<MemberDecl> members = new ArrayList<>();
            for(var member : classDecl.getMembers()) {
                var dafnyMember = translateMember(member);
                if (dafnyMember != null) {
                    members.add(translateMember(member));
                }
            }
            return new ClassDecl(toOrigin(classDecl), name(classDecl, classDecl.name), null,
                    List.of(), members, List.of(), false);
        }
        throw new NotImplementedException(tree.getClass().getName());
    }
    
    class NotImplementedException extends RuntimeException {
        public NotImplementedException(String message) {
            super(message);
        }
    }
    
    Name name(JCTree tree, com.sun.tools.javac.util.Name name) {
        var token = toToken(tree.getPreferredPosition());
        return new Name(new SourceOrigin(token, token, token), name.toString());
    }
    
    MemberDecl translateMember(JCTree member) {
        var origin = toOrigin(member);
        if (member instanceof JCTree.JCMethodDecl method) {
            var name = name(method, method.name);

            if (method.name.contentEquals("<init>")) {
                return null;
            }
            
            var annotations = method.getModifiers().getAnnotations();
            var annotationsByName = annotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().toString(),
                    a -> a));
            
            var isStatic = (method.getModifiers().flags & Flags.STATIC) == Flags.STATIC;
            var returnType = toType(method.getReturnType());

            List<Formal> ins = method.getParameters().map(jvd -> 
                    new Formal(toOrigin(jvd), name(jvd, jvd.getName()), toType(jvd.getType()), false, true,
            null, null, false, false, false, null));
            
            if (annotationsByName.containsKey("Pure")) {
                if (method.body.stats.size() != 1) {
                    throw new RuntimeException("Pure method should have only one statement");
                }
                if (returnType == null) {
                    throw new RuntimeException("Pure method should have a return type");
                }
                
                var statement = method.body.stats.getFirst();
                if (statement instanceof JCTree.JCReturn returnStatement) {
                    var body = toExpr(returnStatement.expr);
                    return new com.aws.jverify.generated.Function(origin, name, null, isStatic, false, null, List.of(),
                            ins, List.of(), List.of(), new Specification<FrameExpression>(origin, List.of(), null),
                            new Specification<Expression>(origin, List.of(), null), false, null, returnType,
                            body, null, null
                            );
                } else {
                    throw new RuntimeException("Pure method statement should be a return");
                }
            }
            
            if (method.name.contentEquals("<init>")) {
                return null;
            } else {
                List<Name> returnNames = new ArrayList<>();
                List<AttributedExpression> req = new ArrayList<>();
                List<AttributedExpression> ens = new ArrayList<>();
                Function<JCTree.JCStatement, Boolean> function = 
                        (JCTree.JCStatement s) -> findHeaderStatement(s, req, ens, returnNames);

                var statements = processStatementsWithHeader(method.getBody().stats, function);

                if (returnNames.size() > 1) {
                    throw new RuntimeException("Ensures clauses may introduce only one return variable name");
                }
                var outs = new ArrayList<Formal>();
                if (returnType != null) {
                    Name returnName;
                    if (returnNames.size() == 1) {
                        returnName = returnNames.getFirst();
                    } else {
                        returnName = new Name(origin, "r");
                    }
                    var f = new Formal(toOrigin(method.getReturnType()), returnName, returnType,
                            false, false, null, null, false, false, false, null);
                    outs.add(f);
                }

                if (annotationsByName.containsKey("Proof")) {
                    return new Method(origin, name, null, isStatic, false, null, List.of(),
                            ins, req, ens, new Specification<FrameExpression>(origin, List.of(), null),
                            new Specification<>(origin, List.of(), null), outs,
                            new Specification<FrameExpression>(origin, List.of(), null),
                            new BlockStmt(origin, null, statements), false);
                } else {
                    return new Method(origin, name, null, isStatic, false, null, List.of(),
                            ins, req, ens, new Specification<FrameExpression>(origin, List.of(), null),
                            new Specification<>(origin, List.of(), null), outs,
                            new Specification<FrameExpression>(origin, List.of(), null),
                            new BlockStmt(origin, null, statements), false);
                }
            }
        }
        throw new NotImplementedException(member.getClass().getName());
    }

    private List<Statement> processStatementWithHeader(JCTree.JCStatement statement,
                                                        Function<JCTree.JCStatement, Boolean> checkPossibleHeader) {
        var statements = statement instanceof JCTree.JCBlock block ? block.stats : List.of(statement);
        return processStatementsWithHeader(statements, checkPossibleHeader);
    }
    
    private List<Statement> processStatementsWithHeader(List<JCTree.JCStatement> stats, 
                                                        Function<JCTree.JCStatement, Boolean> checkPossibleHeader) {
        List<Statement> statements = new ArrayList<>();
        boolean inHeader = true;
        for(var index = 0; index < stats.size(); index++) {
            var statement = stats.get(index);
            if (inHeader) {
                if (!checkPossibleHeader.apply(statement)) {
                    inHeader = false;
                }
            }
            if (!inHeader) {
                statements.add(translateStatement(statement));
            }
        }
        return statements;
    }

    private boolean findHeaderStatement(JCTree.JCStatement statement, 
                                     List<AttributedExpression> preconditions, 
                                     List<AttributedExpression> postconditions, 
                                        List<Name> returnNames) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement)) {
            return false;
        }
        var expr = expressionStatement.getExpression();
        if (!(expr instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }

        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        if (!fromJVerify(methodSymbol)) {
            return false;
        }
        if (methodSymbol.getQualifiedName().contentEquals("precondition")) {
            if (invocation.args.size() != 1) {
                throw new RuntimeException("A precondition call may have only one argument");
            }
            preconditions.add(new AttributedExpression(toExpr(invocation.getArguments().getFirst()), null, null));
            return true;
        }

        if (methodSymbol.getQualifiedName().contentEquals("postcondition")) {
            if (invocation.args.size() != 1) {
                throw new RuntimeException("An postcondition call may have only one argument");
            }
            var first = invocation.getArguments().getFirst();
            if (first instanceof JCTree.JCLambda lambda) {
                if (lambda.getParameters().size() != 1) {
                    throw new RuntimeException("An ensures call lambda may take only one argument");
                }
                var parameter = lambda.getParameters().getFirst();
                returnNames.add(new Name(toOrigin(lambda), parameter.getName().toString()));
                postconditions.add(new AttributedExpression(toExpr(lambda.getBody()), null, null));
                return true;
            } if (first instanceof JCTree.JCExpression expression) {
                var dafnyExpr = toExpr(expression);
                postconditions.add(new AttributedExpression(dafnyExpr, null, null));
                return true;
            } else {
                throw new RuntimeException("An ensures clause must take either a lambda or an expression");
            }
        }
        return false;
    }

    private Expression toExpr(JCTree tree) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExpr(expression);
        }
        throw new NotImplementedException(tree.getClass().getName());
    }
    
    private Expression toExpr(JCTree.JCExpression expr) {
        var origin = toOrigin(expr);
        if (expr instanceof JCTree.JCConditional conditional) {
            var condition = toExpr(conditional.getCondition());
            var thenBranch = toExpr(conditional.getTrueExpression());
            var elseBranch = toExpr(conditional.getFalseExpression());
            return new ITEExpr(origin, false, condition, thenBranch, elseBranch);
        } else if (expr instanceof JCTree.JCBinary binary) {
            var left = toExpr(binary.getLeftOperand());
            var right = toExpr(binary.getRightOperand());
            //
            return new BinaryExpr(origin, toDafny(binary.getOperator()),left, right);
        } else if (expr instanceof JCTree.JCIdent identifier) {
            return new NameSegment(origin, identifier.getName().toString(), null);
        } else if (expr instanceof JCTree.JCLiteral literal) {
            return new LiteralExpr(origin, literal.value);
        } else if (expr instanceof JCTree.JCMethodInvocation invocation) {
            var target = toExpr(invocation.getMethodSelect());
            var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(origin, null, toExpr(a), false)).toList();
            return new ApplySuffix(origin, target, null, 
                    new ActualBindings(origin, argBindings), 
                    origin.getEndToken());
        } else if (expr instanceof JCTree.JCParens parens) {
            return toExpr(parens.getExpression());
        }
        throw new NotImplementedException(expr.getClass().getName());
    }
    
    BinaryExprOpcode toDafny(Symbol.OperatorSymbol operator) {
        switch(operator.name.toString()) {
            case "<": return BinaryExprOpcode.Lt;
            case "-": return BinaryExprOpcode.Sub;
            case "+": return BinaryExprOpcode.Add;
            case "==": return BinaryExprOpcode.Eq;
            case "<=": return BinaryExprOpcode.Le;
            default: throw new NotImplementedException("Operator" + operator.name);
        }
    }

    private @Nullable Type toType(JCTree tree) {
        return toType(tree, null);
    }
    
    private @Nullable Type toType(JCTree tree, @Nullable SourceOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> toOrigin(tree));
        if (tree instanceof JCTree.JCPrimitiveTypeTree primitiveTypeTree) {
            if (primitiveTypeTree.getPrimitiveTypeKind() == TypeKind.VOID)
                return null;

            if (primitiveTypeTree.getPrimitiveTypeKind() == TypeKind.INT) {
                var mirrors = primitiveTypeTree.type.getAnnotationMirrors();
                var natAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Nat.class.getName())).findFirst();
                var isNat = natAnnotation.isPresent();
                var boundedAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Unbounded.class.getName())).findFirst();
                var isBounded = boundedAnnotation.isEmpty();
                if (isBounded) {
                    if (isNat) {
                        return new UserDefinedType(origin, new NameSegment(origin, "nat32", null));
                    } else {
                        return new UserDefinedType(origin, new NameSegment(origin, "int32", null));
                    }
                } else {
                    if (isNat) {
                        return new UserDefinedType(origin, new NameSegment(origin, "nat", null));
                    } else {
                        return new IntType(origin);
                    }
                }
            }
        }
            
        throw new NotImplementedException(tree.getClass().getName());
    }

    private SourceOrigin toOrigin(JCTree node) {
        return toOrigin(node, node);
    }
    
    private SourceOrigin toOrigin(JCTree node, JCTree centerNode) {
        int endPos = TreeInfo.getEndPos(node, compilationUnit.endPositions);
        var startToken = toToken(TreeInfo.getStartPos(node));
        return new SourceOrigin(startToken,
                endPos == Position.NOPOS ? startToken : toToken(endPos), 
                toToken(centerNode.pos));
    }
    
    private Token toToken(int pos) {
        return new Token(
                (int)compilationUnit.getLineMap().getLineNumber(pos), 
                (int) compilationUnit.getLineMap().getColumnNumber(pos) + 1);
    }
    
    private Statement translateStatement(JCTree.JCStatement statement) {
        SourceOrigin origin = toOrigin(statement);
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            if (expr instanceof JCTree.JCMethodInvocation invocation) {

                var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                if (fromJVerify(methodSymbol)) {
                    if (methodSymbol.getQualifiedName().contentEquals("check")) {
                        if (invocation.args.size() != 1) {
                            throw new RuntimeException("Check should have a single argument");
                        }
                        return new AssertStmt(toOrigin(invocation), null,
                                translateExpression(invocation.args.getFirst()), null);
                    }
                } else {
                    var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(origin, null, toExpr(a), false)).toList();
                    ApplySuffix applySuffix = new ApplySuffix(toOrigin(invocation), toExpr(invocation.getMethodSelect()), null,
                            new ActualBindings(origin, argBindings), null);
                    return new AssignStatement(origin, null, List.of(),
                            List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);
                }
            }
            if (expressionStatement.getExpression() instanceof JCTree.JCAssign assign) {
                var e = toExpr(assign.getExpression());
                List<Expression> lhss = List.of(toExpr(assign.getVariable()));
                List<AssignmentRhs> rhss = List.of(new ExprRhs(e.getOrigin(), null, e));
                return new AssignStatement(e.getOrigin(), null, lhss, rhss, false);
            }
        } else if (statement instanceof JCTree.JCAssert assertStmt) {
            return new AssertStmt(origin, null,
                    translateExpression(assertStmt.getCondition()), null);
        } else if (statement instanceof JCTree.JCIf ifStatement) {
            var condition = toExpr(ifStatement.getCondition());
            var thenBranch = (BlockStmt)translateStatement(ifStatement.getThenStatement());
            BlockStmt elseBranch = null;
            if (ifStatement.getElseStatement() != null) {
                elseBranch = (BlockStmt) translateStatement(ifStatement.getElseStatement());
            }
            return new IfStmt(origin, null, false, condition, 
                    thenBranch, elseBranch);
        } else if (statement instanceof JCTree.JCBlock blockStatement) {
            return new BlockStmt(origin, null, 
                    blockStatement.getStatements().map(this::translateStatement).stream().toList());
        } else if (statement instanceof JCTree.JCReturn returnStatement) {
            var expr = returnStatement.getExpression();
            return new ReturnStmt(origin, null, 
                    List.of(new ExprRhs(toOrigin(expr), null, toExpr(expr))));
        } else if (statement instanceof JCTree.JCVariableDecl variableDecl) {
            LocalVariable localVariable = new LocalVariable(origin, 
                    variableDecl.getName().toString(), toType(variableDecl.getType(), origin), false);
            ConcreteAssignStatement initializer = null;
            if (variableDecl.getInitializer() != null) {
                var e = toExpr(variableDecl.getInitializer());
                List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
                List<AssignmentRhs> rhss = List.of(new ExprRhs(e.getOrigin(), null, e));
                initializer = new AssignStatement(e.getOrigin(), null, lhss, rhss, false);
            }
            
            return new VarDeclStmt(origin, null, List.of(localVariable), initializer);
        } else if (statement instanceof JCTree.JCWhileLoop whileLoop) {

            List<AttributedExpression> invariants = new ArrayList<>();
            List<Expression> decreases = new ArrayList<>();
            List<FrameExpression> modifies = new ArrayList<>();
            Function<JCTree.JCStatement, Boolean> function =
                    (JCTree.JCStatement s) -> findWhileHeaderStatement(s, invariants, decreases, modifies);
            var statements = processStatementWithHeader(whileLoop.body, function);
            var condition = toExpr(whileLoop.getCondition());
            return new WhileStmt(origin, null, invariants, new Specification<>(origin, decreases, null),
                    new Specification<>(origin, modifies, null), new BlockStmt(origin, null, statements),
                    condition);
        }
        throw new NotImplementedException(statement.getClass().getName());
    }

    private boolean findWhileHeaderStatement(JCTree.JCStatement statement,
                                             List<AttributedExpression> invariants,
                                             List<Expression> decreases, 
                                             List<FrameExpression> modifies) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement)) {
            return false;
        }
        var expr = expressionStatement.getExpression();
        if (!(expr instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }

        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        if (!fromJVerify(methodSymbol)) {
            return false;
        }
        
        if (methodSymbol.getQualifiedName().contentEquals("invariant")) {
            if (invocation.args.size() != 1) {
                throw new RuntimeException("invariant should have a single argument");
            }
            invariants.add(new AttributedExpression(toExpr(invocation.getArguments().getFirst()), null, null));
            return true;
        }

        if (methodSymbol.getQualifiedName().contentEquals("decreases")) {
            if (invocation.args.size() != 1) {
                throw new RuntimeException("decreases should have a single argument");
            }
            var first = invocation.getArguments().getFirst();
            throw new NotImplementedException("decreases");
        }
        return false;
    }

    private static boolean fromJVerify(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.getEnclosingElement().getQualifiedName().contentEquals(JVERIFY_CLASS);
    }

    private Expression translateExpression(JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCLiteral literal) {
            if (literal.typetag == TypeTag.BOOLEAN) {
                return new LiteralExpr(toOrigin(literal), literal.value == (Object)0 ? false : 1);
            }
        }
        throw new NotImplementedException(expression.getClass().getName());
    }
}
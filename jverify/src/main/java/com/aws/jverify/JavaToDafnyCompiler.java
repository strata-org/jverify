package com.aws.jverify;

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
import javax.swing.plaf.basic.BasicPopupMenuSeparatorUI;
import javax.tools.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

public class JavaToDafnyCompiler {
    public static final String JVERIFY_CLASS = "com.aws.jverify.JVerify";
    JCTree.JCCompilationUnit compilationUnit;

    public FileModuleDefinition analyzeJavaCode(List<JavaFileObject> files) {
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Context context = new Context();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);

        List<String> options = List.of();
                
//                Arrays.asList(
//                "-classpath", "/path/to/your.jar:/path/to/other.jar"
//        );
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,  // Add classpath here
                null,
                files
        );

        // Parse the source file into an AST
        compilationUnit = (JCTree.JCCompilationUnit) task.parse().iterator().next();
        
        task.analyze();

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
              throw new RuntimeException();   
            }
        }

        ArrayList<TopLevelDecl> topLevelDecls = new ArrayList<>();
        
        var empty = new Token(0,1);
        var emptyOrigin = new SourceOrigin(empty, empty, empty);
        var emptyName = new Name(emptyOrigin, "unnamed");

        for(var typeDecl : compilationUnit.getTypeDecls()) {
            topLevelDecls.add(translateTypeDeclaration(typeDecl));
        }
        
        var fileDefinition = new FileModuleDefinition(emptyOrigin, emptyName, List.of(), ModuleKindEnum.Concrete, 
                null, null, topLevelDecls);
        return fileDefinition;
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
        throw new NotImplementedException();
    }
    
    class NotImplementedException extends RuntimeException {}
    
    Name name(JCTree tree, com.sun.tools.javac.util.Name name) {
        var token = toToken(tree.getPreferredPosition());
        return new Name(new SourceOrigin(token, token, token), name.toString());
    }
    
    MemberDecl translateMember(JCTree member) {
        var origin = toOrigin(member);
        if (member instanceof JCTree.JCMethodDecl method) {
            List<Statement> statements = new ArrayList<>();
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

            if (annotationsByName.containsKey("Pure")) {
                if (method.body.stats.size() != 1) {
                    throw new RuntimeException();
                }
                if (returnType == null) {
                    throw new RuntimeException();
                }
                
                var statement = method.body.stats.getFirst();
                if (statement instanceof JCTree.JCReturn returnStatement) {
                    var body = toExpr(returnStatement.expr);
                    return new Function(origin, name, null, isStatic, false, List.of(),
                            List.of(), List.of(), List.of(), new Specification<>(origin, List.of(), null),
                            new Specification<>(origin, List.of(), null), false, null, returnType,
                            body, null, null, null
                            );
                } else {
                    throw new RuntimeException();
                }
            }
            
            if (method.name.contentEquals("<init>")) {
                return null;
            } else {
                List<Name> returnNames = new ArrayList<>();
                List<AttributedExpression> req = new ArrayList<>();
                List<AttributedExpression> ens = new ArrayList<>();
                
                boolean inHeader = true;
                for(var index = 0;index < method.body.stats.size(); index++) {
                    var statement = method.body.stats.get(index);
                    if (inHeader) {
                        if (!findHeaderStatement(statement, req, ens, returnNames)) {
                            inHeader = false;
                        }
                    }
                    if (!inHeader) {
                        statements.add(translateStatement(statement));
                    }
                }                    
                
                if (returnNames.size() > 1) {
                    throw new RuntimeException();
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
                
                return new Method(origin, name, null, isStatic, false, List.of(),
                        List.of(), req, ens, new Specification<FrameExpression>(origin, List.of(), null),
                        new Specification<>(origin, List.of(), null), outs,
                        new Specification<>(origin, List.of(), null), 
                        new BlockStmt(origin, null, statements), null, false);
            }
        }
        throw new NotImplementedException();
    }

    private boolean findHeaderStatement(JCTree.JCStatement statement, 
                                     List<AttributedExpression> requireses, 
                                     List<AttributedExpression> ensureses, 
                                        List<Name> returnNames) {
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            if (expr instanceof JCTree.JCMethodInvocation invocation) {

                var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                if (fromJVerify(methodSymbol)) {
                    if (methodSymbol.getQualifiedName().contentEquals("requires")) {
                        if (invocation.args.size() != 1) {
                            throw new RuntimeException();
                        }
                        requireses.add(new AttributedExpression(toExpr(invocation.getArguments().getFirst()), null, null));
                        return true;
                    }

                    if (methodSymbol.getQualifiedName().contentEquals("ensures")) {
                        if (invocation.args.size() != 1) {
                            throw new RuntimeException();
                        }
                        var first = invocation.getArguments().getFirst();
                        if (first instanceof JCTree.JCLambda lambda) {
                            if (lambda.getParameters().size() != 1) {
                                throw new RuntimeException();
                            }
                            var parameter = lambda.getParameters().getFirst();
                            returnNames.add(new Name(toOrigin(lambda), parameter.getName().toString()));
                            ensureses.add(new AttributedExpression(toExpr(lambda.getBody()), null, null));
                            return true;
                        } else {
                            throw new RuntimeException();
                        }
                    }
                }
            }
        }
        return false;
    }

    private Expression toExpr(JCTree tree) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExpr(expression);
        }
        throw new NotImplementedException();
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
            return new NameSegment(origin, identifier.getName().toString(), List.of());
        } else if (expr instanceof JCTree.JCLiteral literal) {
            return new LiteralExpr(origin, literal.value);
        } else if (expr instanceof JCTree.JCMethodInvocation invocation) {
            var target = toExpr(invocation.getMethodSelect());
            var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(origin, null, toExpr(a), false)).toList();
            return new ApplySuffix(origin, target, origin, 
                    new ActualBindings(origin, argBindings), 
                    origin.getEndToken());
        } else if (expr instanceof JCTree.JCParens parens) {
            return toExpr(parens.getExpression());
        }
        throw new NotImplementedException();
    }
    
    BinaryExprOpcode toDafny(Symbol.OperatorSymbol operator) {
        switch(operator.name.toString()) {
            case "<": return BinaryExprOpcode.Le;
            case "-": return BinaryExprOpcode.Sub;
            case "+": return BinaryExprOpcode.Add;
            case "==": return BinaryExprOpcode.Eq;
            default: throw new RuntimeException();
        }
    }

    private @Nullable Type toType(JCTree tree) {
        var origin = toOrigin(tree);
        if (tree instanceof JCTree.JCPrimitiveTypeTree primitiveTypeTree) {
            if (primitiveTypeTree.getPrimitiveTypeKind() == TypeKind.VOID)
                return null;

            if (primitiveTypeTree.getPrimitiveTypeKind() == TypeKind.INT) {
                var isNat = primitiveTypeTree.type.getAnnotationsByType(Nat.class) != null;
                var isBounded = primitiveTypeTree.type.getAnnotationsByType(Unbounded.class) == null;
                if (isBounded) {
                    if (isNat) {
                        return new UserDefinedType(origin, new NameSegment(origin, "nat32", List.of()));
                    } else {
                        return new UserDefinedType(origin, new NameSegment(origin, "int32", List.of()));
                    }
                } else {
                    if (isNat) {
                        return new UserDefinedType(origin, new NameSegment(origin, "nat", List.of()));
                    } else {
                        return new IntType(origin);
                    }
                }
            }
        }
            
        throw new RuntimeException();
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
                            throw new RuntimeException();
                        }
                        return new AssertStmt(toOrigin(invocation), null,
                                translateExpression(invocation.args.getFirst()), null);
                    }
                }
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
            LocalVariable localVariable = new LocalVariable(toOrigin(variableDecl.nameexpr), 
                    variableDecl.name.toString(), toType(variableDecl.getType()), false);
            ConcreteAssignStatement initializer = null;
            if (variableDecl.getInitializer() != null) {
                var e = toExpr(variableDecl.getInitializer());
                initializer = new AssignStatement(e.getOrigin(), null, List.of());
            }
            
            return new VarDeclStmt(origin, null, List.of(localVariable), initializer)
        }
        throw new NotImplementedException();
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
        throw new NotImplementedException();
    }
}
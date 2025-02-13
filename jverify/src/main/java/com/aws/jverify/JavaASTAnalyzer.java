package com.aws.jverify;

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
import com.sun.tools.javac.util.Position;
import com.aws.jverify.generated.*;

import javax.tools.*;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JavaASTAnalyzer {
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
            } if (method.name.contentEquals("assert2")) {
                return null;
            } else {
                for(var statement : method.body.stats) {
                    statements.add(translateStatement(statement));
                }
                
                var isStatic = (method.getModifiers().flags & Flags.STATIC) == Flags.STATIC;
                return new Method(origin, name, null, isStatic, false, List.of(),
                        List.of(), List.of(), List.of(), new Specification<FrameExpression>(origin, List.of(), null),
                        new Specification<Expression>(origin, List.of(), null), List.of(),
                        new Specification<FrameExpression>(origin, List.of(), null), 
                        new BlockStmt(origin, null, statements), null, false);
            }
        }
        throw new NotImplementedException();
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
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            if (expr instanceof JCTree.JCMethodInvocation invocation) {

                var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                if (methodSymbol.getQualifiedName().contentEquals("check")
                  && methodSymbol.getEnclosingElement().getQualifiedName().contentEquals("com.aws.jverify.JVerify")) {
                    if (invocation.args.size() != 1) {
                        throw new RuntimeException();
                    }
                    return new AssertStmt(toOrigin(invocation), null, 
                            translateExpression(invocation.args.getFirst()), null);
                }
            }
        } else if (statement instanceof JCTree.JCAssert assertStmt) {
            return new AssertStmt(toOrigin(assertStmt), null,
                    translateExpression(assertStmt.getCondition()), null);
        }
        throw new NotImplementedException();
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
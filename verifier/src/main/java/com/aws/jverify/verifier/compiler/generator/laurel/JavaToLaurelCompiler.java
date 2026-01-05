package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.laurel.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.List;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;

    public JavaToLaurelCompiler(Context context) {
        lowerer = new JavaLowerer(context);
    }

    public Node analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        List<Procedure> staticProcedures = new ArrayList<>();
        for (var compilationUnit : loweredResult.parsed()) {
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            staticProcedures.addAll(visitor.procedures);
        }
        return new Program(null, staticProcedures);
    }

    private static class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            // Only process static methods
            if ((method.mods.flags & Flags.STATIC) != 0) {
                String methodName = method.name.toString();
                StmtExpr body = convertMethodBody(method.body);
                procedures.add(new Procedure_(null, methodName, body));
            }
            super.visitMethodDef(method);
        }

        private StmtExpr convertMethodBody(JCTree.JCBlock block) {
            if (block == null) {
                return new Block(null, List.of());
            }

            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : block.stats) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) {
                    statements.add(converted);
                }
            }
            return new Block(null, statements);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                    new Assert(null, convertExpression(assertStmt.cond));
                case JCTree.JCExpressionStatement exprStmt ->
                    convertExpression(exprStmt.expr);
                case JCTree.JCBlock block ->
                    convertMethodBody(block);
                default -> null;
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr) {
            if (expr instanceof JCTree.JCLiteral literal) {
                if (literal.value instanceof Boolean boolValue) {
                    return new LiteralBool(null, boolValue);
                }
            }
            return null;
        }
    }
}

package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.AssertStmt;
import com.aws.jverify.generated.AssumeStmt;
import com.aws.jverify.generated.Statement;
import com.aws.jverify.laurel.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.aws.jverify.verifier.compiler.generator.dafny.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.generator.dafny.ExpressionContext;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.List;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
    }

    public Node analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        List<Procedure> staticProcedures = new ArrayList<>();
        for (var compilationUnit : loweredResult.parsed()) {
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            staticProcedures.addAll(visitor.procedures);
        }
        return new Program(SourceRange.NONE, staticProcedures);
    }
    
    static SourceRange toSourceRange(JCTree node) {
        return SourceRange.NONE;
    }

    private static class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            // Only process static methods
            if ((method.mods.flags & Flags.STATIC) != 0) {
                String methodName = method.name.toString();
                StmtExpr body = convertMethodBody(method.body);
                procedures.add(new Procedure_(toSourceRange(method), methodName, body));
            }
            super.visitMethodDef(method);
        }

        private @Nullable StmtExpr convertMethodBody(JCTree.JCBlock block) {
            if (block == null) {
                return null;
            }

            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : block.stats) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) {
                    statements.add(converted);
                }
            }
            return new Block(toSourceRange(block), statements);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                    new Assert(toSourceRange(assertStmt), convertExpression(assertStmt.cond));
                case JCTree.JCExpressionStatement exprStmt ->
                    convertExpression(exprStmt.expr);
                case JCTree.JCBlock block ->
                    convertMethodBody(block);
                default -> null; //throw new RuntimeException("Not supported " + statement.getClass().getName());
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr) {
            if (expr instanceof JCTree.JCLiteral literal) {
                if (literal.typetag == TypeTag.BOOLEAN) {
                    return new LiteralBool(toSourceRange(literal), (int)literal.value != 0);
                }
            } else if (expr instanceof JCTree.JCMethodInvocation methodInvocation) {
                var jverifyMethod = JVerifyUtils.getJVerifyMethod(methodInvocation);
                if (jverifyMethod != null) {
                    return getJVerifyStatement(methodInvocation, jverifyMethod);
                }
            }
            
            return null;
        }

        @org.checkerframework.checker.nullness.qual.Nullable
        public StmtExpr getJVerifyStatement(JCTree.JCMethodInvocation invocation,
                                             Symbol.MethodSymbol jverifyMethod) {

            var name = jverifyMethod.getQualifiedName().toString();
            if (name.equals("check")) {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("Check should have a single argument");
                }
                return new Assert(toSourceRange(invocation),
                        convertExpression(invocation.args.getFirst()));
            } if (name.equals("assume")) {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("Check should have a single argument");
                }
                return new Assume(toSourceRange(invocation),
                        convertExpression(invocation.args.getFirst()));
            }
            return null;
        }
    }
}

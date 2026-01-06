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
import com.sun.tools.javac.util.Position;
import org.apfloat.internal.ParallelThreeNTTConvolutionStrategy;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
    }

    public List<LaurelFile> analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        for (var compilationUnit : loweredResult.parsed()) {
            List<Procedure> staticProcedures = new ArrayList<>();
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            staticProcedures.addAll(visitor.procedures);
            var lineOffsets = new ArrayList<Integer>();
            Position.LineMap lineMap = compilationUnit.getLineMap();
            int lineCount = 0;
            try {
                lineCount = lineMap.getLineNumber(compilationUnit.sourcefile.getCharContent(true).length());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for(var line = 1; line < lineCount; line++) {
                lineOffsets.add(lineMap.getStartPosition(line));
            }
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(), 
                    new Program(SourceRange.NONE, staticProcedures), lineOffsets));
        }
        return result;
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

package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.Nullable;
import com.aws.jverify.common.Position;
import com.aws.jverify.laurel.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.laurel.FilesMap;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;
    JCTree.JCCompilationUnit currentCompilationUnit;

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
    }

    /**
     * Result of analyzing Java code, containing both the compiled Laurel files
     * and a DiagnosticHelper for mapping offsets to positions.
     */
    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();
        for (var compilationUnit : loweredResult.parsed()) {
            currentCompilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            List<Procedure> staticProcedures = new ArrayList<>(visitor.procedures);
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(),
                    new Program(SourceRange.NONE, staticProcedures)));

            lineMaps.put(compilationUnit.sourcefile.toUri(), compilationUnit.getLineMap());
        }

        FilesMap filesMap = (uri, offset) -> {
            var lineMap = lineMaps.get(uri);
            if (lineMap == null) {
                throw new RuntimeException("Could not find line map for " + uri);
            }

            long line = lineMap.getLineNumber(offset);
            long lineStart = lineMap.getStartPosition(line);
            long column = offset - lineStart;

            return new Position((int)line, (int)column + 1);
        };

        return new AnalysisResult(result, filesMap);
    }
    
    SourceRange toSourceRange(JCTree node) {
        int startPos = TreeInfo.getStartPos(node);
        int endPos = TreeInfo.getEndPos(node, currentCompilationUnit.endPositions);
        if (endPos == -1) {
            endPos = startPos;
        }
        return new SourceRange(startPos, endPos);
    }

    private class StaticMethodCollector extends TreeScanner {
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

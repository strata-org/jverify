package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.*;
import org.strata.jverify.verifier.VerifierOptions;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.frontend.JavaLowerer;
import org.strata.jverify.verifier.compiler.simplifications.JVerifyUtils;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import org.strata.jverify.verifier.laurel.FilesMap;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.*;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    JCTree.JCCompilationUnit currentCompilationUnit;

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
    }

    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();
        boolean first = true;
        for (var compilationUnit : loweredResult.parsed()) {
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            currentCompilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            List<Command> commands = new ArrayList<>();
            if (first) {
                commands.addAll(getPredefinedTypes());
                first = false;
            }
            for (var proc : visitor.procedures) {
                commands.add(new Command.ProcedureCommand(SourceRange.NONE, proc));
            }
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(), commands));
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

    private List<Command> getPredefinedTypes() {
        return List.of(
            makeConstrainedType("int8", -128L, 127L),
            makeConstrainedType("int16", -32768L, 32767L),
            makeConstrainedType("int32", -2147483648L, 2147483647L),
            makeConstrainedType("int64", -9223372036854775808L, 9223372036854775807L)
        );
    }

    private Command makeConstrainedType(String name, long min, long max) {
        var sr = toSourceRange(currentCompilationUnit);
        var x = new StmtExpr.Identifier(sr, "x");
        var minExpr = longLiteral(sr, min);
        var maxExpr = longLiteral(sr, max);
        var constraint = new StmtExpr.And(sr, new StmtExpr.Ge(sr, x, minExpr), new StmtExpr.Le(sr, x, maxExpr));
        var witness = new StmtExpr.Int(sr, java.math.BigInteger.ZERO);
        return new Command.ConstrainedTypeCommand(sr,
                new ConstrainedType.Of(sr, name, "x", new LaurelType.IntType(sr), constraint, witness));
    }

    /** Create a Laurel integer literal for any long value, wrapping negatives in Neg. */
    private static StmtExpr longLiteral(SourceRange sr, long val) {
        if (val >= 0) return new StmtExpr.Int(sr, java.math.BigInteger.valueOf(val));
        return new StmtExpr.Neg(sr, new StmtExpr.Int(sr, java.math.BigInteger.valueOf(val).negate()));
    }

    private LaurelType translateType(com.sun.tools.javac.code.Type type) {
        return switch (type.getTag()) {
            case INT -> new LaurelType.CompositeType(SourceRange.NONE, "int32");
            case SHORT -> new LaurelType.CompositeType(SourceRange.NONE, "int16");
            case BYTE -> new LaurelType.CompositeType(SourceRange.NONE, "int8");
            case LONG -> new LaurelType.CompositeType(SourceRange.NONE, "int64");
            case BOOLEAN -> new LaurelType.BoolType(SourceRange.NONE);
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            if ((method.mods.flags & Flags.STATIC) != 0) {
                String methodName = method.name.toString();

                List<Parameter> params = new ArrayList<>();
                for (var param : method.params) {
                    params.add(new Parameter.Of(SourceRange.NONE, param.name.toString(), translateType(param.type)));
                }

                ReturnType retType = null;
                if (method.restype != null && method.restype.type != null
                        && method.restype.type.getTag() != TypeTag.VOID) {
                    retType = new ReturnType.Of(SourceRange.NONE, translateType(method.restype.type));
                }

                List<RequiresClause> requires = new ArrayList<>();
                List<EnsuresClause> ensures = new ArrayList<>();
                StmtExpr methodBody = null;

                if (method.body != null) {
                    MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                    for (var pre : contract.preconditions()) {
                        requires.add(new RequiresClause.Of(SourceRange.NONE, convertExpression(pre.get()), Optional.empty()));
                    }
                    for (var post : contract.postconditions()) {
                        var postExpr = post.get();
                        if (postExpr instanceof JCTree.JCLambda lambda && lambda.params.size() == 1) {
                            var paramName = lambda.params.getFirst().name.toString();
                            var renames = Map.of(paramName, "result");
                            ensures.add(new EnsuresClause.Of(SourceRange.NONE, convertLambdaBody(lambda, renames), Optional.empty()));
                        } else {
                            ensures.add(new EnsuresClause.Of(SourceRange.NONE, convertExpression(postExpr), Optional.empty()));
                        }
                    }

                    var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(method.body);
                    if (!implStatements.isEmpty()) {
                        List<StmtExpr> stmts = new ArrayList<>();
                        for (var statement : implStatements) {
                            StmtExpr converted = convertStatement(statement);
                            if (converted != null) {
                                stmts.add(converted);
                            }
                        }
                        methodBody = new StmtExpr.Block(toSourceRange(method.body), stmts);
                    }
                }

                Body optBody = methodBody != null
                        ? new Body.Body_(SourceRange.NONE, methodBody)
                        : null;

                // Only wrap ensures/modifies in OpaqueSpec when the method has no body
                // (i.e., it is truly opaque). Methods with a body are transparent.
                Optional<OpaqueSpec> opaqueSpec = (methodBody == null && !ensures.isEmpty())
                        ? Optional.of(new OpaqueSpec.Of(SourceRange.NONE, ensures, List.of()))
                        : Optional.empty();

                boolean isPure = jverifyUtils.isPure(method.sym);
                Procedure proc = isPure
                        ? new Procedure.Function(toSourceRange(method), methodName, params,
                                Optional.ofNullable(retType), Optional.empty(), requires, Optional.empty(), opaqueSpec, Optional.ofNullable(optBody))
                        : new Procedure.Procedure_(toSourceRange(method), methodName, params,
                                Optional.ofNullable(retType), Optional.empty(), requires, Optional.empty(), opaqueSpec, Optional.ofNullable(optBody));
                procedures.add(proc);
            }
            super.visitMethodDef(method);
        }

        private StmtExpr convertBlock(JCTree.JCBlock blk) {
            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) {
                    statements.add(converted);
                }
            }
            return new StmtExpr.Block(toSourceRange(blk), statements);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        new StmtExpr.Assert(toSourceRange(assertStmt), convertExpression(assertStmt.cond), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr);
                case JCTree.JCBlock blk -> convertBlock(blk);
                case JCTree.JCVariableDecl varDecl -> {
                    LaurelType type = translateType(varDecl.type);
                    Initializer optAssign = varDecl.init != null
                            ? new Initializer.Of(SourceRange.NONE, convertExpression(varDecl.init))
                            : null;
                    yield new StmtExpr.VarDecl(toSourceRange(varDecl), varDecl.name.toString(),
                            Optional.of(new TypeAnnotation.Of(SourceRange.NONE, type)), Optional.ofNullable(optAssign));
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond);
                    StmtExpr thenBranch = convertStatement(ifStmt.thenpart);
                    ElseBranch elseB = null;
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart);
                        if (elseStmt != null) {
                            elseB = new ElseBranch.Of(SourceRange.NONE, elseStmt);
                        }
                    }
                    yield new StmtExpr.IfThenElse(toSourceRange(ifStmt), cond, thenBranch, Optional.ofNullable(elseB));
                }
                case JCTree.JCReturn retStmt -> {
                    if (retStmt.expr != null) {
                        yield new StmtExpr.Return(toSourceRange(retStmt), convertExpression(retStmt.expr));
                    }
                    yield null;
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    StmtExpr cond = convertExpression(whileStmt.cond);
                    if (whileStmt.body instanceof JCTree.JCBlock loopBlock) {
                        var parts = extractLoopParts(loopBlock);
                        yield new StmtExpr.While(toSourceRange(whileStmt), cond, parts.invariants, parts.body);
                    }
                    yield new StmtExpr.While(toSourceRange(whileStmt), cond, List.of(), convertStatement(whileStmt.body));
                }
                case JCTree.JCForLoop forLoop -> {
                    if (forLoop.init.size() > 1 || forLoop.step.size() > 1)
                        throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                    StmtExpr init = forLoop.init.isEmpty() ? new StmtExpr.Block(toSourceRange(forLoop), List.of())
                            : convertStatement(forLoop.init.getFirst());
                    StmtExpr cond = forLoop.cond != null
                            ? convertExpression(forLoop.cond)
                            : new StmtExpr.LiteralBool(toSourceRange(forLoop), true);
                    StmtExpr step = forLoop.step.isEmpty() ? new StmtExpr.Block(toSourceRange(forLoop), List.of())
                            : convertStatement(forLoop.step.getFirst());
                    List<InvariantClause> invariants = List.of();
                    StmtExpr loopBody;
                    if (forLoop.body instanceof JCTree.JCBlock loopBlock) {
                        var parts = extractLoopParts(loopBlock);
                        invariants = parts.invariants;
                        loopBody = parts.body;
                    } else {
                        loopBody = convertStatement(forLoop.body);
                    }
                    yield new StmtExpr.ForLoop(toSourceRange(forLoop), init, cond, step, invariants, loopBody);
                }
                default -> throw new JavaViolationException("Unsupported statement: " + statement.getClass().getSimpleName());
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr) {
            return convertExpression(expr, Map.of());
        }

        private StmtExpr convertLambdaBody(JCTree.JCLambda lambda, Map<String, String> renames) {
            if (lambda.body instanceof JCTree.JCExpression exprBody) {
                return convertExpression(exprBody, renames);
            } else if (lambda.body instanceof JCTree.JCReturn ret && ret.expr != null) {
                return convertExpression(ret.expr, renames);
            } else if (lambda.body instanceof JCTree.JCBlock blk && blk.stats.size() == 1
                    && blk.stats.getFirst() instanceof JCTree.JCReturn ret && ret.expr != null) {
                return convertExpression(ret.expr, renames);
            }
            throw new JavaViolationException("Unsupported lambda body");
        }

        private record LoopParts(List<InvariantClause> invariants, StmtExpr body) {}

        private LoopParts extractLoopParts(JCTree.JCBlock loopBlock) {
            MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
            List<InvariantClause> invariants = new ArrayList<>();
            for (var inv : loopContract.loopInvariants()) {
                invariants.add(new InvariantClause.Of(SourceRange.NONE, convertExpression(inv.get())));
            }
            var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
            List<StmtExpr> stmts = new ArrayList<>();
            for (var s : implStatements) {
                StmtExpr converted = convertStatement(s);
                if (converted != null) stmts.add(converted);
            }
            return new LoopParts(invariants, new StmtExpr.Block(toSourceRange(loopBlock), stmts));
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield new StmtExpr.Identifier(toSourceRange(ident), renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        new StmtExpr.Assign(toSourceRange(asgn), convertExpression(asgn.lhs, renames), convertExpression(asgn.rhs, renames));
                case JCTree.JCConditional cond ->
                        new StmtExpr.IfThenElse(toSourceRange(cond), convertExpression(cond.cond, renames),
                                convertExpression(cond.truepart, renames),
                                Optional.of(new ElseBranch.Of(toSourceRange(cond.falsepart), convertExpression(cond.falsepart, renames))));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    String calleeName = methodSym.getSimpleName().toString();
                    List<StmtExpr> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(convertExpression(arg, renames));
                    }
                    yield new StmtExpr.Call(toSourceRange(invocation),
                            new StmtExpr.Identifier(toSourceRange(invocation), calleeName), args);
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            var sr = toSourceRange(literal);
            return switch (literal.typetag) {
                case BOOLEAN -> new StmtExpr.LiteralBool(sr, (int) literal.value != 0);
                case INT, LONG, SHORT, BYTE -> {
                    long val = ((Number) literal.value).longValue();
                    yield longLiteral(sr, val);
                }
                default -> throw new JavaViolationException("Unsupported literal type: " + literal.typetag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            StmtExpr lhs = convertExpression(binary.lhs, renames);
            StmtExpr rhs = convertExpression(binary.rhs, renames);
            SourceRange sr = toSourceRange(binary);
            return switch (binary.getTag()) {
                case PLUS -> new StmtExpr.Add(sr, lhs, rhs);
                case MINUS -> new StmtExpr.Sub(sr, lhs, rhs);
                case MUL -> new StmtExpr.Mul(sr, lhs, rhs);
                case DIV -> new StmtExpr.DivT(sr, lhs, rhs);
                case MOD -> new StmtExpr.ModT(sr, lhs, rhs);
                case EQ -> new StmtExpr.Eq(sr, lhs, rhs);
                case NE -> new StmtExpr.Neq(sr, lhs, rhs);
                case GT -> new StmtExpr.Gt(sr, lhs, rhs);
                case LT -> new StmtExpr.Lt(sr, lhs, rhs);
                case GE -> new StmtExpr.Ge(sr, lhs, rhs);
                case LE -> new StmtExpr.Le(sr, lhs, rhs);
                case AND -> new StmtExpr.AndThen(sr, lhs, rhs);
                case OR -> new StmtExpr.OrElse(sr, lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            StmtExpr inner = convertExpression(unary.arg, renames);
            SourceRange sr = toSourceRange(unary);
            return switch (unary.getTag()) {
                case NOT -> new StmtExpr.Not(sr, inner);
                case NEG -> new StmtExpr.Neg(sr, inner);
                default -> throw new JavaViolationException("Unsupported unary op: " + unary.getTag());
            };
        }

        private StmtExpr convertJVerifyCall(JCTree.JCMethodInvocation invocation,
                                            Symbol.MethodSymbol jverifyMethod,
                                            Map<String, String> renames) {
            var name = jverifyMethod.getQualifiedName().toString();
            SourceRange sr = toSourceRange(invocation);
            return switch (name) {
                case "check" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("check should have a single argument");
                    yield new StmtExpr.Assert(sr, convertExpression(invocation.args.getFirst(), renames), Optional.empty());
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield new StmtExpr.Assume(sr, convertExpression(invocation.args.getFirst(), renames));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    yield new StmtExpr.Or(sr, new StmtExpr.Not(sr, convertExpression(invocation.args.get(0), renames)),
                            convertExpression(invocation.args.get(1), renames));
                }
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr qBody = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
                        var ty = translateType(p.type);
                        qBody = name.equals("forall")
                                ? new StmtExpr.ForallExpr(sr, p.name.toString(), ty, Optional.empty(), qBody)
                                : new StmtExpr.ExistsExpr(sr, p.name.toString(), ty, Optional.empty(), qBody);
                    }
                    yield qBody;
                }
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}

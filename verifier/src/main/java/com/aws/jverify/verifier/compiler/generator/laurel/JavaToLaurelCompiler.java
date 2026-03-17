package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.common.Position;
import com.aws.jverify.laurel.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import com.aws.jverify.verifier.laurel.FilesMap;
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
                commands.add(new ProcedureCommand(SourceRange.NONE, proc));
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
            constrainedTypeCommand("int8", -128L, 127L),
            constrainedTypeCommand("int16", -32768L, 32767L),
            constrainedTypeCommand("int32", -2147483648L, 2147483647L),
            constrainedTypeCommand("int64", -9223372036854775808L, 9223372036854775807L)
        );
    }

    private Command constrainedTypeCommand(String name, long min, long max) {
        var sr = toSourceRange(currentCompilationUnit);
        var x = new Identifier(sr, "x");
        var minExpr = min >= 0
                ? new Int(sr, java.math.BigInteger.valueOf(min))
                : new Neg(sr, new Int(sr, java.math.BigInteger.valueOf(min).negate()));
        var maxExpr = new Int(sr, java.math.BigInteger.valueOf(max));
        var constraint = new And(sr,
                new Ge(sr, x, minExpr),
                new Le(sr, x, maxExpr));
        var witness = new Int(sr, java.math.BigInteger.ZERO);
        return new ConstrainedTypeCommand(sr,
                new ConstrainedType_(sr, name, "x", new IntType(sr), constraint, witness));
    }

    private LaurelType translateType(com.sun.tools.javac.code.Type type) {
        return switch (type.getTag()) {
            case INT -> new CompositeType(SourceRange.NONE, "int32");
            case SHORT -> new CompositeType(SourceRange.NONE, "int16");
            case BYTE -> new CompositeType(SourceRange.NONE, "int8");
            case LONG -> new CompositeType(SourceRange.NONE, "int64");
            case BOOLEAN -> new BoolType(SourceRange.NONE);
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            // TODO: Use NameCompiler for unique method names to avoid collisions across classes
            // TODO: Filter synthetic methods (JVerifyUtils.isSynthetic) and constructors (JVerifyUtils.isConstructor)
            // TODO: Respect @Verify(false) annotation
            if ((method.mods.flags & Flags.STATIC) != 0) {
                String methodName = method.name.toString();

                // Parameters
                List<Parameter> params = new ArrayList<>();
                for (var param : method.params) {
                    params.add(new Parameter_(SourceRange.NONE, param.name.toString(), translateType(param.type)));
                }

                // Return type
                Optional<OptionalReturnType> returnType = Optional.empty();
                if (method.restype != null && method.restype.type != null
                        && method.restype.type.getTag() != TypeTag.VOID) {
                    returnType = Optional.of(new OptionalReturnType_(SourceRange.NONE, translateType(method.restype.type)));
                }

                // Extract contracts from the method body
                List<RequiresClause> requires = new ArrayList<>();
                List<EnsuresClause> ensures = new ArrayList<>();
                StmtExpr body = null;

                if (method.body != null) {
                    MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                    for (var pre : contract.preconditions()) {
                        requires.add(new RequiresClause_(SourceRange.NONE, convertExpression(pre.get()), Optional.empty()));
                    }
                    for (var post : contract.postconditions()) {
                        var postExpr = post.get();
                        if (postExpr instanceof JCTree.JCLambda lambda && lambda.params.size() == 1) {
                            var paramName = lambda.params.getFirst().name.toString();
                            var renames = Map.of(paramName, "result");
                            ensures.add(new EnsuresClause_(SourceRange.NONE, convertLambdaBody(lambda, renames), Optional.empty()));
                        } else {
                            ensures.add(new EnsuresClause_(SourceRange.NONE, convertExpression(postExpr), Optional.empty()));
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
                        body = new Block(toSourceRange(method.body), stmts);
                    }
                }

                Optional<OptionalBody> optBody = body != null
                        ? Optional.of(new OptionalBody_(SourceRange.NONE, body))
                        : Optional.empty();

                boolean isPure = jverifyUtils.isPure(method.sym);
                Procedure proc = isPure
                        ? new Function(toSourceRange(method), methodName, params,
                                returnType, Optional.empty(), requires, ensures, List.of(), optBody)
                        : new Procedure_(toSourceRange(method), methodName, params,
                                returnType, Optional.empty(), requires, ensures, List.of(), optBody);
                procedures.add(proc);
            }
            super.visitMethodDef(method);
        }

        private StmtExpr convertBlock(JCTree.JCBlock block) {
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
                        new Assert(toSourceRange(assertStmt), convertExpression(assertStmt.cond), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr);
                case JCTree.JCBlock block -> convertBlock(block);
                case JCTree.JCVariableDecl varDecl -> {
                    LaurelType type = translateType(varDecl.type);
                    Optional<OptionalAssignment> optAssign = varDecl.init != null
                            ? Optional.of(new OptionalAssignment_(SourceRange.NONE, convertExpression(varDecl.init)))
                            : Optional.empty();
                    yield new VarDecl(toSourceRange(varDecl), varDecl.name.toString(),
                            Optional.of(new OptionalType_(SourceRange.NONE, type)), optAssign);
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond);
                    StmtExpr thenBranch = convertStatement(ifStmt.thenpart);
                    Optional<OptionalElse> elseBranch = Optional.empty();
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart);
                        if (elseStmt != null) {
                            elseBranch = Optional.of(new OptionalElse_(SourceRange.NONE, elseStmt));
                        }
                    }
                    yield new IfThenElse(toSourceRange(ifStmt), cond, thenBranch, elseBranch);
                }
                case JCTree.JCReturn retStmt -> {
                    if (retStmt.expr != null) {
                        yield new Return(toSourceRange(retStmt), convertExpression(retStmt.expr));
                    }
                    // void return — skip
                    yield null;
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    StmtExpr cond = convertExpression(whileStmt.cond);
                    if (whileStmt.body instanceof JCTree.JCBlock loopBlock) {
                        var parts = extractLoopParts(loopBlock);
                        yield new While(toSourceRange(whileStmt), cond, parts.invariants, parts.body);
                    }
                    yield new While(toSourceRange(whileStmt), cond, List.of(), convertStatement(whileStmt.body));
                }
                case JCTree.JCForLoop forLoop -> {
                    List<StmtExpr> outerStmts = new ArrayList<>();
                    for (var init : forLoop.init) {
                        StmtExpr converted = convertStatement(init);
                        if (converted != null) outerStmts.add(converted);
                    }
                    StmtExpr cond = forLoop.cond != null
                            ? convertExpression(forLoop.cond)
                            : new LiteralBool(toSourceRange(forLoop), true);
                    List<InvariantClause> invariants = List.of();
                    List<StmtExpr> bodyStmts = new ArrayList<>();
                    if (forLoop.body instanceof JCTree.JCBlock loopBlock) {
                        var parts = extractLoopParts(loopBlock);
                        invariants = parts.invariants;
                        // Unwrap the block to append step statements
                        if (parts.body instanceof Block b) {
                            bodyStmts.addAll(b.stmts());
                        } else {
                            bodyStmts.add(parts.body);
                        }
                    } else {
                        StmtExpr bodyConverted = convertStatement(forLoop.body);
                        if (bodyConverted != null) bodyStmts.add(bodyConverted);
                    }
                    for (var step : forLoop.step) {
                        StmtExpr converted = convertStatement(step);
                        if (converted != null) bodyStmts.add(converted);
                    }
                    StmtExpr whileBody = new Block(toSourceRange(forLoop.body), bodyStmts);
                    outerStmts.add(new While(toSourceRange(forLoop), cond, invariants, whileBody));
                    yield new Block(toSourceRange(forLoop), outerStmts);
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
            } else if (lambda.body instanceof JCTree.JCBlock block && block.stats.size() == 1
                    && block.stats.getFirst() instanceof JCTree.JCReturn ret && ret.expr != null) {
                return convertExpression(ret.expr, renames);
            }
            throw new JavaViolationException("Unsupported lambda body");
        }

        private record LoopParts(List<InvariantClause> invariants, StmtExpr body) {}

        private LoopParts extractLoopParts(JCTree.JCBlock loopBlock) {
            MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
            List<InvariantClause> invariants = new ArrayList<>();
            for (var inv : loopContract.loopInvariants()) {
                invariants.add(new InvariantClause_(SourceRange.NONE, convertExpression(inv.get())));
            }
            var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
            List<StmtExpr> stmts = new ArrayList<>();
            for (var s : implStatements) {
                StmtExpr converted = convertStatement(s);
                if (converted != null) stmts.add(converted);
            }
            return new LoopParts(invariants, new Block(toSourceRange(loopBlock), stmts));
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield new Identifier(toSourceRange(ident), renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign assign ->
                        new Assign(toSourceRange(assign), convertExpression(assign.lhs, renames), convertExpression(assign.rhs, renames));
                case JCTree.JCConditional cond ->
                        new IfThenElse(toSourceRange(cond), convertExpression(cond.cond, renames),
                                convertExpression(cond.truepart, renames),
                                Optional.of(new OptionalElse_(toSourceRange(cond.falsepart), convertExpression(cond.falsepart, renames))));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    // Regular static method call
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    String calleeName = methodSym.getSimpleName().toString();
                    List<StmtExpr> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(convertExpression(arg, renames));
                    }
                    yield new Call(toSourceRange(invocation),
                            new Identifier(toSourceRange(invocation), calleeName), args);
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            var sr = toSourceRange(literal);
            return switch (literal.typetag) {
                case BOOLEAN -> new LiteralBool(sr, (int) literal.value != 0);
                case INT, LONG -> {
                    long val = ((Number) literal.value).longValue();
                    yield val >= 0
                            ? new Int(sr, java.math.BigInteger.valueOf(val))
                            : new Neg(sr, new Int(sr, java.math.BigInteger.valueOf(val).negate()));
                }
                default -> throw new JavaViolationException("Unsupported literal type: " + literal.typetag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            StmtExpr lhs = convertExpression(binary.lhs, renames);
            StmtExpr rhs = convertExpression(binary.rhs, renames);
            SourceRange sr = toSourceRange(binary);
            return switch (binary.getTag()) {
                case PLUS -> new Add(sr, lhs, rhs);
                case MINUS -> new Sub(sr, lhs, rhs);
                case MUL -> new Mul(sr, lhs, rhs);
                case DIV -> new DivT(sr, lhs, rhs);
                case MOD -> new ModT(sr, lhs, rhs);
                case EQ -> new Eq(sr, lhs, rhs);
                case NE -> new Neq(sr, lhs, rhs);
                case GT -> new Gt(sr, lhs, rhs);
                case LT -> new Lt(sr, lhs, rhs);
                case GE -> new Ge(sr, lhs, rhs);
                case LE -> new Le(sr, lhs, rhs);
                case AND -> new And(sr, lhs, rhs);
                case OR -> new Or(sr, lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            StmtExpr inner = convertExpression(unary.arg, renames);
            SourceRange sr = toSourceRange(unary);
            return switch (unary.getTag()) {
                case NOT -> new Not(sr, inner);
                case NEG -> new Neg(sr, inner);
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
                    yield new Assert(sr, convertExpression(invocation.args.getFirst(), renames), Optional.empty());
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield new Assume(sr, convertExpression(invocation.args.getFirst(), renames));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    yield new Implies(sr, convertExpression(invocation.args.get(0), renames),
                            convertExpression(invocation.args.get(1), renames));
                }
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr body = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
                        var ty = translateType(p.type);
                        body = name.equals("forall")
                                ? new ForallExpr(sr, p.name.toString(), ty, Optional.empty(), body)
                                : new ExistsExpr(sr, p.name.toString(), ty, Optional.empty(), body);
                    }
                    yield body;
                }
                // TODO: Support old() in postconditions
                // TODO: Support break/continue (blocked on Strata grammar — needs Exit node)
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}

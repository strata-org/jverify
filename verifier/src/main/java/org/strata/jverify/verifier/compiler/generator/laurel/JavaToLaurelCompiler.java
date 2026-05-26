package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.*;
import org.strata.jverify.verifier.VerifierOptions;
import org.strata.jverify.verifier.compiler.JavaViolationException;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.frontend.JavaLowerer;
import org.strata.jverify.verifier.compiler.simplifications.JVerifyUtils;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import org.strata.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
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

import static org.strata.jverify.laurel.Laurel.*;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    private final Reporter reporter;
    private final VerifyAnnotationCompiler annotationCompiler;
    JCTree.JCCompilationUnit currentCompilationUnit;

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        annotationCompiler = VerifyAnnotationCompiler.instance(context);
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
            reporter.compilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);
            List<Command> commands = new ArrayList<>();
            if (first) {
                commands.addAll(getPredefinedTypes());
                first = false;
            }
            for (var proc : visitor.procedures) {
                commands.add(procedureCommand(proc));
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
            makeConstrainedType("int64", -9223372036854775808L, 9223372036854775807L),
            makeConstrainedType("char", 0L, 65535L)
        );
    }

    private Command makeConstrainedType(String name, long min, long max) {
        var sr = toSourceRange(currentCompilationUnit);
        var x = identifier(sr, "x");
        var minExpr = longLiteral(sr, min);
        var maxExpr = longLiteral(sr, max);
        var constraint = and(sr, ge(sr, x, minExpr), le(sr, x, maxExpr));
        var witness = int_(sr, 0);
        return constrainedTypeCommand(sr, constrainedType(sr, name, "x", intType(sr), constraint, witness));
    }

    /** Create a Laurel integer literal for any long value, wrapping negatives in Neg. */
    private static StmtExpr longLiteral(SourceRange sr, long val) {
        if (val >= 0) return int_(sr, val);
        return neg(sr, new StmtExpr.Int(sr, java.math.BigInteger.valueOf(val).negate()));
    }

    private LaurelType translateType(com.sun.tools.javac.code.Type type) {
        return switch (type.getTag()) {
            case INT -> compositeType("int32");
            case SHORT -> compositeType("int16");
            case BYTE -> compositeType("int8");
            case LONG -> compositeType("int64");
            case CHAR -> compositeType("char");
            case BOOLEAN -> boolType();
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    private static String qualifiedMethodName(Symbol.MethodSymbol sym) {
        return sym.outermostClass().name + "_" + sym.name;
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();
        private int labelCounter = 0;

        /** Label stack entry for break/continue resolution. */
        private record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {}
        private final Deque<LabelEntry> labelStack = new ArrayDeque<>();
        private String pendingLabel = null;

        /** Check if a statement tree contains break or continue (at the current loop level). */
        private boolean containsBreakOrContinue(JCTree.JCStatement stmt) {
            boolean[] found = {false};
            stmt.accept(new TreeScanner() {
                @Override public void visitBreak(JCTree.JCBreak tree) { found[0] = true; }
                @Override public void visitContinue(JCTree.JCContinue tree) { found[0] = true; }
                // Don't descend into nested loops — their break/continue don't target us
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) {}
                @Override public void visitForLoop(JCTree.JCForLoop tree) {}
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) {}
            });
            return found[0];
        }

        private String freshLabel(String prefix) {
            return prefix + "_" + (labelCounter++);
        }

        /** Set up loop labels, push to stack, execute body, pop stack. */
        private StmtExpr withLoopLabels(JCTree.JCStatement loopBody, java.util.function.BiFunction<String, String, StmtExpr> body) {
            boolean needsLabels = pendingLabel != null || containsBreakOrContinue(loopBody);
            String breakLbl = needsLabels ? freshLabel("loop_break") : null;
            String continueLbl = needsLabels ? freshLabel("loop_continue") : null;
            String javaLabel = pendingLabel;
            pendingLabel = null;
            labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
            try {
                return body.apply(breakLbl, continueLbl);
            } finally {
                labelStack.pop();
            }
        }

        private String resolveBreakLabel(JCTree.JCBreak brk) {
            if (brk.label != null) {
                String target = brk.label.toString();
                for (var entry : labelStack) {
                    if (target.equals(entry.javaLabel)) return entry.breakLabel;
                }
            } else if (!labelStack.isEmpty()) {
                return labelStack.peek().breakLabel;
            }
            throw new JavaViolationException("break target not found");
        }

        private String resolveContinueLabel(JCTree.JCContinue cont) {
            if (cont.label != null) {
                String target = cont.label.toString();
                for (var entry : labelStack) {
                    if (target.equals(entry.javaLabel) && entry.continueLabel != null) return entry.continueLabel;
                }
            } else {
                for (var entry : labelStack) {
                    if (entry.continueLabel != null) return entry.continueLabel;
                }
            }
            throw new JavaViolationException("continue target not found");
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            if ((method.mods.flags & Flags.STATIC) != 0) {
                try {
                    translateStaticMethod(method);
                } catch (JavaViolationException e) {
                    reporter.reportError(method, "translatorError", e.getMessage());
                    annotationCompiler.markSkipped(currentCompilationUnit, method);
                }
            }
            super.visitMethodDef(method);
        }

        private void translateStaticMethod(JCTree.JCMethodDecl method) {
            // TODO: when overloaded methods are supported, disambiguate names
            //  (e.g. by appending parameter type suffixes) to avoid duplicate procedure names in Laurel.
            String methodName = qualifiedMethodName(method.sym);

            List<Parameter> params = new ArrayList<>();
            for (var param : method.params) {
                params.add(parameter(param.name.toString(), translateType(param.type)));
            }

            Optional<ReturnType> retType = Optional.empty();
            if (method.restype != null && method.restype.type != null
                    && method.restype.type.getTag() != TypeTag.VOID) {
                retType = Optional.of(returnType(translateType(method.restype.type)));
            }

            List<RequiresClause> requires = new ArrayList<>();
            List<EnsuresClause> ensures = new ArrayList<>();
            StmtExpr methodBody = null;

            if (method.body != null) {
                MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                for (var pre : contract.preconditions()) {
                    requires.add(requiresClause(convertExpression(pre.get()), Optional.empty()));
                }
                for (var post : contract.postconditions()) {
                    var postExpr = post.get();
                    if (postExpr instanceof JCTree.JCLambda lambda && lambda.params.size() == 1) {
                        var paramName = lambda.params.getFirst().name.toString();
                        var renames = Map.of(paramName, "result");
                        ensures.add(ensuresClause(convertLambdaBody(lambda, renames), Optional.empty()));
                    } else {
                        ensures.add(ensuresClause(convertExpression(postExpr), Optional.empty()));
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
                    methodBody = block(toSourceRange(method.body), stmts);
                }
            }

            Optional<Body> optBody = methodBody != null
                    ? Optional.of(body(methodBody))
                    : Optional.empty();

            boolean isPure = jverifyUtils.isPure(method.sym);
            // Strata rejects transparent (visible-body) procedures unless they're functional;
            // emit an OpaqueSpec to mark the body opaque otherwise. Pure functions stay
            // transparent only when they have no ensures clauses (the schema can't carry
            // ensures without an OpaqueSpec wrapper).
            // modifies is always empty: jverify doesn't yet emit modifies clauses.
            boolean canStayTransparent = isPure && ensures.isEmpty();
            Optional<OpaqueSpec> optSpec = canStayTransparent
                    ? Optional.empty()
                    : Optional.of(opaqueSpec(ensures, List.of()));

            Procedure proc = isPure
                    ? function(toSourceRange(method), methodName, params,
                            retType, Optional.empty(), requires, Optional.empty(), optSpec, optBody)
                    : procedure(toSourceRange(method), methodName, params,
                            retType, Optional.empty(), requires, Optional.empty(), optSpec, optBody);
            procedures.add(proc);
        }

        private StmtExpr convertBlock(JCTree.JCBlock blk) {
            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) {
                    statements.add(converted);
                }
            }
            return block(toSourceRange(blk), statements);
        }

        /**
         * Emit a while loop with optional break/continue label wrapping.
         *
         * When labels are present, produces (for a for-loop with break/continue):
         * <pre>
         * labelledBlock(breakLbl, {
         *   preamble...        // e.g. init for for-loops, sentinel decl for do-while
         *   while (cond)
         *     invariants: [...]
         *   {
         *     labelledBlock(continueLbl, { body });
         *     step;            // null for while/do-while
         *   }
         * })
         * </pre>
         * break emits as exit(breakLbl), continue as exit(continueLbl).
         * The step is outside the continue label so continue skips the body but still runs the step.
         * @param sr source range
         * @param cond loop condition
         * @param invariants loop invariants
         * @param loopBody the translated loop body
         * @param step optional step expression (for-loops); null for while/do-while
         * @param preamble statements to emit before the while (e.g. init, sentinel decl)
         * @param breakLbl break label (null if no labels needed)
         * @param continueLbl continue label (null if no labels needed)
         */
        private StmtExpr emitLoop(SourceRange sr, StmtExpr cond, List<InvariantClause> invariants,
                                  StmtExpr loopBody, StmtExpr step, List<StmtExpr> preamble,
                                  String breakLbl, String continueLbl) {
            if (breakLbl != null) {
                StmtExpr wrappedBody = labelledBlock(sr, List.of(loopBody), continueLbl);
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = block(sr, List.of(wrappedBody, step));
                } else {
                    whileBody = wrappedBody;
                }
                StmtExpr whileNode = while_(sr, cond, invariants, whileBody);
                List<StmtExpr> outerStmts = new ArrayList<>(preamble);
                outerStmts.add(whileNode);
                return labelledBlock(sr, outerStmts, breakLbl);
            } else {
                if (step != null) {
                    // Use forLoop IR node for simple for-loops without break/continue
                    StmtExpr init = preamble.isEmpty() ? block(sr, List.of()) : preamble.getFirst();
                    return forLoop(sr, init, cond, step, invariants, loopBody);
                }
                StmtExpr whileNode = while_(sr, cond, invariants, loopBody);
                if (preamble.isEmpty()) {
                    return whileNode;
                }
                List<StmtExpr> stmts = new ArrayList<>(preamble);
                stmts.add(whileNode);
                return block(sr, stmts);
            }
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        assert_(toSourceRange(assertStmt), convertExpression(assertStmt.cond), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr);
                case JCTree.JCBlock blk -> convertBlock(blk);
                case JCTree.JCVariableDecl varDecl -> {
                    LaurelType type = translateType(varDecl.type);
                    Optional<Initializer> optAssign = varDecl.init != null
                            ? Optional.of(initializer(convertExpression(varDecl.init)))
                            : Optional.empty();
                    yield varDecl(toSourceRange(varDecl), varDecl.name.toString(),
                            Optional.of(typeAnnotation(type)), optAssign);
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond);
                    StmtExpr thenBranch = convertStatement(ifStmt.thenpart);
                    Optional<ElseBranch> elseB = Optional.empty();
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart);
                        if (elseStmt != null) {
                            elseB = Optional.of(elseBranch(elseStmt));
                        }
                    }
                    yield ifThenElse(toSourceRange(ifStmt), cond, thenBranch, elseB);
                }
                case JCTree.JCReturn retStmt -> {
                    if (retStmt.expr != null) {
                        yield return_(toSourceRange(retStmt), convertExpression(retStmt.expr));
                    }
                    yield null;
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    var sr = toSourceRange(whileStmt);
                    yield withLoopLabels(whileStmt.body, (breakLbl, continueLbl) -> {
                        StmtExpr cond = convertExpression(whileStmt.cond);
                        var parts = extractLoopParts(whileStmt.body);
                        return emitLoop(sr, cond, parts.invariants, parts.body, null, List.of(),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCForLoop forLoop -> {
                    if (forLoop.init.size() > 1 || forLoop.step.size() > 1)
                        throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                    var sr = toSourceRange(forLoop);
                    yield withLoopLabels(forLoop.body, (breakLbl, continueLbl) -> {
                        StmtExpr init = forLoop.init.isEmpty() ? block(sr, List.of())
                                : convertStatement(forLoop.init.getFirst());
                        StmtExpr cond = forLoop.cond != null
                                ? convertExpression(forLoop.cond)
                                : literalBool(sr, true);
                        StmtExpr step = forLoop.step.isEmpty() ? block(sr, List.of())
                                : convertStatement(forLoop.step.getFirst());
                        var parts = extractLoopParts(forLoop.body);
                        return emitLoop(sr, cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhileStmt -> {
                    var sr = toSourceRange(doWhileStmt);
                    yield withLoopLabels(doWhileStmt.body, (breakLbl, continueLbl) -> {
                        // Desugar: var $first_N = true; while($first_N || cond) { $first_N = false; body }
                        String sentinel = "$first_" + labelCounter;
                        StmtExpr sentinelDecl = varDecl(sr, sentinel,
                                Optional.of(typeAnnotation(boolType())),
                                Optional.of(initializer(literalBool(sr, true))));
                        StmtExpr cond = orElse(sr, identifier(sr, sentinel), convertExpression(doWhileStmt.cond));
                        StmtExpr sentinelReset = assign(sr, identifier(sr, sentinel), literalBool(sr, false));

                        var parts = extractLoopParts(doWhileStmt.body);
                        StmtExpr loopBody = block(sr, List.of(sentinelReset, parts.body));
                        return emitLoop(sr, cond, parts.invariants, loopBody, null, List.of(sentinelDecl),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCBreak breakStmt -> {
                    yield exit(toSourceRange(breakStmt), resolveBreakLabel(breakStmt));
                }
                case JCTree.JCContinue contStmt -> {
                    yield exit(toSourceRange(contStmt), resolveContinueLabel(contStmt));
                }
                case JCTree.JCLabeledStatement labeledStmt -> {
                    // Set the pending label so the next loop picks it up
                    pendingLabel = labeledStmt.label.toString();
                    // If the body is a loop, it will consume pendingLabel
                    // If not, wrap in a labelledBlock for break-out-of-block
                    if (labeledStmt.body instanceof JCTree.JCWhileLoop
                            || labeledStmt.body instanceof JCTree.JCForLoop
                            || labeledStmt.body instanceof JCTree.JCDoWhileLoop) {
                        yield convertStatement(labeledStmt.body);
                    } else {
                        // Non-loop labeled statement: only break is valid (no continue)
                        String breakLbl = freshLabel("label_break");
                        labelStack.push(new LabelEntry(pendingLabel, breakLbl, null));
                        pendingLabel = null;
                        try {
                            StmtExpr body = convertStatement(labeledStmt.body);
                            yield labelledBlock(toSourceRange(labeledStmt), List.of(body), breakLbl);
                        } finally {
                            labelStack.pop();
                        }
                    }
                }
                case JCTree.JCSkip ignored -> null;
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

        private LoopParts extractLoopParts(JCTree.JCStatement body) {
            if (body instanceof JCTree.JCBlock loopBlock) {
                MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
                List<InvariantClause> invariants = new ArrayList<>();
                for (var inv : loopContract.loopInvariants()) {
                    invariants.add(invariantClause(convertExpression(inv.get())));
                }
                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
                List<StmtExpr> stmts = new ArrayList<>();
                for (var s : implStatements) {
                    StmtExpr converted = convertStatement(s);
                    if (converted != null) stmts.add(converted);
                }
                return new LoopParts(invariants, block(toSourceRange(loopBlock), stmts));
            } else {
                return new LoopParts(List.of(), convertStatement(body));
            }
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield identifier(toSourceRange(ident), renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        assign(toSourceRange(asgn), convertExpression(asgn.lhs, renames), convertExpression(asgn.rhs, renames));
                case JCTree.JCConditional cond ->
                        ifThenElse(toSourceRange(cond), convertExpression(cond.cond, renames),
                                convertExpression(cond.truepart, renames),
                                Optional.of(elseBranch(toSourceRange(cond.falsepart), convertExpression(cond.falsepart, renames))));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    String calleeName = qualifiedMethodName(methodSym);
                    List<StmtExpr> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(convertExpression(arg, renames));
                    }
                    yield call(toSourceRange(invocation),
                            identifier(toSourceRange(invocation), calleeName), args);
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            var sr = toSourceRange(literal);
            return switch (literal.typetag) {
                case BOOLEAN -> literalBool(sr, (int) literal.value != 0);
                case INT, LONG, SHORT, BYTE -> {
                    long val = ((Number) literal.value).longValue();
                    yield longLiteral(sr, val);
                }
                case CHAR -> longLiteral(sr, ((Number) literal.value).longValue());
                default -> throw new JavaViolationException("Unsupported literal type: " + literal.typetag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            StmtExpr lhs = convertExpression(binary.lhs, renames);
            StmtExpr rhs = convertExpression(binary.rhs, renames);
            SourceRange sr = toSourceRange(binary);
            return switch (binary.getTag()) {
                case PLUS -> add(sr, lhs, rhs);
                case MINUS -> sub(sr, lhs, rhs);
                case MUL -> mul(sr, lhs, rhs);
                case DIV -> divT(sr, lhs, rhs);
                case MOD -> modT(sr, lhs, rhs);
                case EQ -> eq(sr, lhs, rhs);
                case NE -> neq(sr, lhs, rhs);
                case GT -> gt(sr, lhs, rhs);
                case LT -> lt(sr, lhs, rhs);
                case GE -> ge(sr, lhs, rhs);
                case LE -> le(sr, lhs, rhs);
                case AND -> andThen(sr, lhs, rhs);
                case OR -> orElse(sr, lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            StmtExpr inner = convertExpression(unary.arg, renames);
            SourceRange sr = toSourceRange(unary);
            return switch (unary.getTag()) {
                case NOT -> not(sr, inner);
                case NEG -> neg(sr, inner);
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
                    yield assert_(sr, convertExpression(invocation.args.getFirst(), renames), Optional.empty());
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield assume(sr, convertExpression(invocation.args.getFirst(), renames));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    yield or(sr, not(sr, convertExpression(invocation.args.get(0), renames)),
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
                                ? forallExpr(sr, p.name.toString(), ty, Optional.empty(), qBody)
                                : existsExpr(sr, p.name.toString(), ty, Optional.empty(), qBody);
                    }
                    yield qBody;
                }
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}

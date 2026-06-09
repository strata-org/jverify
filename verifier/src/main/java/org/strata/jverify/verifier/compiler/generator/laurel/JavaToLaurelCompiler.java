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
        var commands = new ArrayList<Command>(List.of(
            makeConstrainedType("int8", -128L, 127L),
            makeConstrainedType("int16", -32768L, 32767L),
            makeConstrainedType("int32", -2147483648L, 2147483647L),
            makeConstrainedType("int64", -9223372036854775808L, 9223372036854775807L),
            makeConstrainedType("char", 0L, 65535L)
        ));

        // Uninterpreted-function declaration for the array-as-map
        // model. `arr.length` (JCFieldAccess in contract positions,
        // where ArrayCompiler doesn't intercept) translates to a call
        // against this. Strata's resolver requires a declaration; the
        // empty body makes the function uninterpreted, so the only
        // relation Strata enforces is "same input -> same output".
        var arrayMap = mapType(intType(), intType());
        commands.add(procedureCommand(function(
            "lengthOf",
            List.of(parameter("arr", arrayMap)),
            Optional.of(returnType(intType())),
            Optional.empty(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty()
        )));
        // arrayGet(arr, idx) reads element `idx` from the Map<int,elem>
        // that models the array. Used by both body-level `arr[i]` (via
        // the JArray.get lowering) and contract-position array reads.
        // Uninterpreted: same (arr, idx) yields the same value.
        commands.add(procedureCommand(function(
            "arrayGet",
            List.of(parameter("arr", arrayMap),
                    parameter("idx", intType())),
            Optional.of(returnType(intType())),
            Optional.empty(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty()
        )));
        return commands;
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
        // Array types: encoded as a Laurel MapType(int, int) — the
        // standard Boogie/SMT array model. The element type is
        // deliberately erased to int (the type the array prelude
        // functions in getPredefinedTypes are declared over), so every
        // array, regardless of element type, type-checks uniformly
        // against lengthOf / arrayGet / arraySet / arrayNew_1. This is
        // sound for the int-family element types (int/short/byte/char,
        // which are int-backed) and is a deliberate imprecision for
        // wider (long), reference (Object[]), and nested array element
        // types in this foundation.
        if (type instanceof com.sun.tools.javac.code.Type.ArrayType) {
            return mapType(intType(), intType());
        }
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

        private StmtExpr convertBlock(JCTree.JCBlock blk, Map<String, String> renames) {
            List<StmtExpr> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement, renames);
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
            return convertStatement(statement, Map.of());
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement, Map<String, String> renames) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        assert_(toSourceRange(assertStmt), convertExpression(assertStmt.cond, renames), Optional.empty());
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr, renames);
                case JCTree.JCBlock blk -> convertBlock(blk, renames);
                case JCTree.JCVariableDecl varDecl -> {
                    LaurelType type = translateType(varDecl.type);
                    Optional<Initializer> optAssign = varDecl.init != null
                            ? Optional.of(initializer(convertExpression(varDecl.init, renames)))
                            : Optional.empty();
                    yield varDecl(toSourceRange(varDecl), varDecl.name.toString(),
                            Optional.of(typeAnnotation(type)), optAssign);
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond, renames);
                    StmtExpr thenBranch = convertStatement(ifStmt.thenpart, renames);
                    Optional<ElseBranch> elseB = Optional.empty();
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart, renames);
                        if (elseStmt != null) {
                            elseB = Optional.of(elseBranch(elseStmt));
                        }
                    }
                    yield ifThenElse(toSourceRange(ifStmt), cond, thenBranch, elseB);
                }
                case JCTree.JCReturn retStmt -> {
                    if (retStmt.expr != null) {
                        yield return_(toSourceRange(retStmt), convertExpression(retStmt.expr, renames));
                    }
                    yield null;
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    var sr = toSourceRange(whileStmt);
                    yield withLoopLabels(whileStmt.body, (breakLbl, continueLbl) -> {
                        StmtExpr cond = convertExpression(whileStmt.cond, renames);
                        var parts = extractLoopParts(whileStmt.body, renames);
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
                                : convertStatement(forLoop.init.getFirst(), renames);
                        StmtExpr cond = forLoop.cond != null
                                ? convertExpression(forLoop.cond, renames)
                                : literalBool(sr, true);
                        StmtExpr step = forLoop.step.isEmpty() ? block(sr, List.of())
                                : convertStatement(forLoop.step.getFirst(), renames);
                        var parts = extractLoopParts(forLoop.body, renames);
                        return emitLoop(sr, cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhile -> {
                    // Desugar do-while using while(true) + exit(!cond):
                    //   labelledBlock(breakLbl, [
                    //     while (true) invariants:[I] {
                    //       labelledBlock(continueLbl, [ body ]);
                    //       if (!cond) exit(breakLbl);
                    //     }
                    //   ])
                    // The exit(!cond) is OUTSIDE the continueLbl block so that
                    // continue re-evaluates the condition (doesn't skip it).
                    // Invariant is checked at while-entry before every iteration
                    // including the first — no soundness gap as discussed in #418.
                    var sr = toSourceRange(doWhile);
                    // Do-while always needs labels: the desugaring itself uses exit(breakLbl)
                    var breakLbl = freshLabel("loop_break");
                    var continueLbl = freshLabel("loop_continue");
                    var javaLabel = pendingLabel;
                    pendingLabel = null;
                    labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
                    try {
                        var cond = convertExpression(doWhile.cond, renames);
                        var parts = extractLoopParts(doWhile.body, renames);
                        var exitIfDone = ifThenElse(sr, not(sr, cond),
                                exit(sr, breakLbl), Optional.empty());
                        var wrappedBody = labelledBlock(sr, List.of(parts.body), continueLbl);
                        var whileBody = block(sr, List.of(wrappedBody, exitIfDone));
                        var whileNode = while_(sr, literalBool(sr, true), parts.invariants, whileBody);
                        yield labelledBlock(sr, List.of(whileNode), breakLbl);
                    } finally {
                        labelStack.pop();
                    }
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
                        yield convertStatement(labeledStmt.body, renames);
                    } else {
                        // Non-loop labeled statement: only break is valid (no continue)
                        String breakLbl = freshLabel("label_break");
                        labelStack.push(new LabelEntry(pendingLabel, breakLbl, null));
                        pendingLabel = null;
                        try {
                            StmtExpr body = convertStatement(labeledStmt.body, renames);
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

        private LoopParts extractLoopParts(JCTree.JCStatement body, Map<String, String> renames) {
            if (body instanceof JCTree.JCBlock loopBlock) {
                MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
                List<InvariantClause> invariants = new ArrayList<>();
                for (var inv : loopContract.loopInvariants()) {
                    invariants.add(invariantClause(convertExpression(inv.get(), renames)));
                }
                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
                List<StmtExpr> stmts = new ArrayList<>();
                for (var s : implStatements) {
                    StmtExpr converted = convertStatement(s, renames);
                    if (converted != null) stmts.add(converted);
                }
                return new LoopParts(invariants, block(toSourceRange(loopBlock), stmts));
            } else {
                return new LoopParts(List.of(), convertStatement(body, renames));
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
                    String simpleName = methodSym.getSimpleName().toString();
                    // Recognise the synthetic JArray.get call that
                    // ArrayCompiler emits for body-level `arr[i]`. We
                    // route get -> arrayGet (declared in the prelude
                    // with same-input/same-output semantics).
                    String ownerName = methodSym.owner != null
                            ? methodSym.owner.getQualifiedName().toString()
                            : "";
                    if (ownerName.equals("org.strata.jverify.builtin.JArray")) {
                        if (simpleName.equals("get")) {
                            calleeName = "arrayGet";
                        }
                    }
                    List<StmtExpr> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(convertExpression(arg, renames));
                    }
                    yield call(toSourceRange(invocation),
                            identifier(toSourceRange(invocation), calleeName), args);
                }
                // Synthesized by SwitchDesugarer to hoist a switch-expression's
                // selector into a fresh local; lowers to a Laurel block (StmtExpr,
                // usable in expression position). javac's Lower emits LetExpr for
                // boxed compound-assign, postops, and pattern switches — all
                // currently unsupported.
                case JCTree.LetExpr letExpr -> {
                    List<StmtExpr> stmts = new ArrayList<>();
                    for (var def : letExpr.defs) {
                        StmtExpr converted = convertStatement(def, renames);
                        if (converted != null) stmts.add(converted);
                    }
                    stmts.add(convertExpression(letExpr.expr, renames));
                    yield block(toSourceRange(letExpr), stmts);
                }
                // Compile-time constant field access (e.g. `Foo.CONST` where
                // CONST is `static final int CONST = 42`). javac keeps these as
                // JCFieldAccess with the constant value attached on the
                // expression's type.
                case JCTree.JCFieldAccess fa when fa.type.constValue() != null ->
                    convertConstantValue(toSourceRange(fa), fa.type.getTag(), fa.type.constValue());
                case JCTree.JCFieldAccess fieldAccess -> {
                    SourceRange sr = toSourceRange(fieldAccess);
                    // Special-case `arr.length` for array-typed receivers.
                    // In JVerify's array-as-map model there is no
                    // intrinsic length; translate to a call to the
                    // uninterpreted Laurel function `lengthOf` we
                    // declare in the prelude (see getPredefinedTypes).
                    // Strata's resolver requires a declaration, so a
                    // pure free identifier wouldn't work; the
                    // declaration also gives same-input/same-output
                    // semantics so multiple references to the same
                    // `arr.length` are consistent.
                    if (fieldAccess.name.toString().equals("length")
                            && fieldAccess.selected.type instanceof
                                com.sun.tools.javac.code.Type.ArrayType) {
                        StmtExpr arr = convertExpression(fieldAccess.selected, renames);
                        yield call(sr, identifier(sr, "lengthOf"),
                                java.util.List.of(arr));
                    }
                    throw new JavaViolationException(
                        "Unsupported field access: " + fieldAccess);
                }
                case JCTree.JCArrayAccess arrayAccess -> {
                    // `arr[i]` for a single-dimension array reads
                    // from the Map<int, elem> we use to model the
                    // array. Translate to an `arrayGet(arr, i)`
                    // call against the Laurel function we declare
                    // in the prelude. Note: ArrayCompiler often
                    // intercepts these in the body and rewrites to
                    // JArray.get; this case is the fallback for
                    // contract-position array reads.
                    SourceRange sr = toSourceRange(arrayAccess);
                    StmtExpr arr = convertExpression(arrayAccess.indexed, renames);
                    StmtExpr idx = convertExpression(arrayAccess.index, renames);
                    yield call(sr, identifier(sr, "arrayGet"),
                            java.util.List.of(arr, idx));
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return convertConstantValue(toSourceRange(literal), literal.typetag, literal.value);
        }

        /// Lower a primitive compile-time constant value to a Laurel literal.
        /// Used by JCLiteral and JCFieldAccess (where the value is attached
        /// via Type.constValue()). Boolean values arrive as Integer 0/1 from
        /// Type.constValue() — Java represents booleans as int internally.
        private StmtExpr convertConstantValue(SourceRange sr, TypeTag tag, Object v) {
            return switch (tag) {
                case BOOLEAN -> literalBool(sr, ((Number) v).intValue() != 0);
                case CHAR, INT, SHORT, BYTE, LONG -> longLiteral(sr, ((Number) v).longValue());
                default -> throw new JavaViolationException("Unsupported constant type tag: " + tag);
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

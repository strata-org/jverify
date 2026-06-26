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

public class JavaToLaurelCompiler {
    private static final String LAUREL_RESULT_BINDING = "result";

    private final JavaLowerer lowerer;
    private final MethodOrLoopContractCompiler contractCompiler;
    private final JVerifyUtils jverifyUtils;
    private final Reporter reporter;
    private final VerifyAnnotationCompiler annotationCompiler;
    JCTree.JCCompilationUnit currentCompilationUnit;

    private final Set<String> referencedCompositeTypes = new LinkedHashSet<>();

    public JavaToLaurelCompiler(Context context) {
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        jverifyUtils = JVerifyUtils.instance(context);
        reporter = Reporter.instance(context);
        annotationCompiler = VerifyAnnotationCompiler.instance(context);
    }

    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

    // --- Helper methods for constructing AST nodes ---

    private static Identifier ident(String name) {
        return new Identifier(name, null, null);
    }

    private static AstNode<StmtExpr> node(StmtExpr expr) {
        return new AstNode<>(expr, null);
    }

    private static AstNode<Variable> nodeVar(Variable v) {
        return new AstNode<>(v, null);
    }

    private static AstNode<HighType> node(HighType type) {
        return new AstNode<>(type, null);
    }

    private static StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return new StmtExpr.PrimitiveOp(op, List.of(args), false);
    }

    private static StmtExpr var_(String name) {
        return new StmtExpr.Var(new Variable.Local(ident(name)));
    }

    private static StmtExpr varDecl(String name, HighType type, StmtExpr init) {
        var param = new Parameter(ident(name), node(type));
        var decl = new Variable.Declare(param);
        if (init == null) {
            return new StmtExpr.Var(decl);
        }
        return new StmtExpr.Assign(List.of(nodeVar(decl)), node(init));
    }

    // --- Main analysis entry point ---

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        if (loweredResult == null) return null;

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();
        boolean first = true;
        Set<String> emittedCompositeTypes = new HashSet<>();
        for (var compilationUnit : loweredResult.parsed()) {
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            currentCompilationUnit = compilationUnit;
            reporter.compilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);

            List<TypeDefinition> types = new ArrayList<>();
            List<Procedure> procs = new ArrayList<>();

            if (first) {
                types.addAll(getPredefinedTypes());
                first = false;
            }
            for (var typeName : referencedCompositeTypes) {
                if (emittedCompositeTypes.add(typeName)) {
                    types.add(new TypeDefinition.Composite(
                        new CompositeType(ident(typeName), List.of(), List.of(), List.of())));
                }
            }
            procs.addAll(visitor.procedures);

            var program = new Program(procs, List.of(), types, List.of());
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(), program));
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

    private List<TypeDefinition> getPredefinedTypes() {
        return List.of(
            makeConstrainedType("int8", -128L, 127L),
            makeConstrainedType("int16", -32768L, 32767L),
            makeConstrainedType("int32", -2147483648L, 2147483647L),
            makeConstrainedType("int64", -9223372036854775808L, 9223372036854775807L),
            makeConstrainedType("char", 0L, 65535L),
            makeConstrainedType("nat7", 0L, 127L),
            makeConstrainedType("nat15", 0L, 32767L),
            makeConstrainedType("nat31", 0L, 2147483647L),
            makeConstrainedType("nat63", 0L, 9223372036854775807L),
            makeConstrainedType("nat", 0L, null)
        );
    }

    private TypeDefinition makeConstrainedType(String name, Long min, Long max) {
        var x = var_("x");
        var bounds = new ArrayList<StmtExpr>();
        if (min != null) {
            bounds.add(primitiveOp(new Operation.Geq(), node(x), node(longLiteral(min))));
        }
        if (max != null) {
            bounds.add(primitiveOp(new Operation.Leq(), node(x), node(longLiteral(max))));
        }
        var constraint = bounds.stream()
                .reduce((a, b) -> primitiveOp(new Operation.And(), node(a), node(b)))
                .orElse(new StmtExpr.LiteralBool(true));
        var witness = new StmtExpr.LiteralInt(0);
        return new TypeDefinition.Constrained(new ConstrainedType(
                ident(name), node(new HighType.TInt()), ident("x"), node(constraint), node(witness)));
    }

    private static StmtExpr longLiteral(long val) {
        if (val >= 0) return new StmtExpr.LiteralInt(val);
        // LiteralInt serializes via ion.newInt(long) which handles negative values directly.
        // However, the Lean-side deserializer may expect Neg(LiteralInt(posVal)) for negative
        // numbers. For Long.MIN_VALUE, -val overflows, so emit the raw negative literal.
        if (val == Long.MIN_VALUE) {
            return new StmtExpr.LiteralInt(val);
        }
        return primitiveOp(new Operation.Neg(), node(new StmtExpr.LiteralInt(-val)));
    }

    private HighType translateType(com.sun.tools.javac.code.Type type) {
        return translateType(type, null);
    }

    private HighType translateType(com.sun.tools.javac.code.Type type, JCTree.JCModifiers modifiers) {
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            String name = classType.tsym.getQualifiedName().toString();
            String sortName = name.replace('$', '.');
            referencedCompositeTypes.add(sortName);
            return new HighType.UserDefined(ident(sortName));
        }
        boolean isNat = JVerifyUtils.isAnnotated(type, org.strata.jverify.Nat.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Nat.class);
        boolean isUnbounded = JVerifyUtils.isAnnotated(type, org.strata.jverify.Unbounded.class)
                || JVerifyUtils.isAnnotated(modifiers, org.strata.jverify.Unbounded.class);

        return switch (type.getTag()) {
            case INT -> integerType(isNat, isUnbounded, "int32", "nat31");
            case SHORT -> integerType(isNat, isUnbounded, "int16", "nat15");
            case BYTE -> integerType(isNat, isUnbounded, "int8", "nat7");
            case LONG -> integerType(isNat, isUnbounded, "int64", "nat63");
            case CHAR -> new HighType.UserDefined(ident("char"));
            case BOOLEAN -> new HighType.TBool();
            default -> throw new JavaViolationException("Unsupported type: " + type);
        };
    }

    private HighType integerType(boolean isNat, boolean isUnbounded, String bounded, String boundedNat) {
        if (isUnbounded) {
            return isNat ? new HighType.UserDefined(ident("nat")) : new HighType.TInt();
        }
        return new HighType.UserDefined(ident(isNat ? boundedNat : bounded));
    }

    private static String qualifiedMethodName(Symbol.MethodSymbol sym) {
        // `outermostClass()` walks the owner chain to the first ClassSymbol,
        // casting along the way; some invocations (record accessors of types
        // declared inside an anonymous class, or built-ins on synthetic Symtab
        // entries) reach here with a non-ClassSymbol owner and throw
        // ClassCastException. Fall back to the immediate owner in that case.
        Symbol.ClassSymbol outer;
        try {
            outer = sym.outermostClass();
        } catch (ClassCastException e) {
            outer = null;
        }
        if (outer != null) {
            // Fully-qualified (package-included) name, sanitised like the
            // CompositeType sort names ('$' -> '.'), so two same-named classes
            // in different packages don't produce colliding procedure names.
            return outer.getQualifiedName().toString().replace('$', '.') + "_" + sym.name;
        }
        Symbol owner = sym.owner;
        String prefix = (owner != null && owner.name != null)
                ? owner.name.toString()
                : "$unknown";
        return prefix + "_" + sym.name;
    }

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();
        private int labelCounter = 0;

        private record LabelEntry(String javaLabel, String breakLabel, String continueLabel) {}
        private final Deque<LabelEntry> labelStack = new ArrayDeque<>();
        private String pendingLabel = null;

        private boolean containsBreakOrContinue(JCTree.JCStatement stmt) {
            boolean[] found = {false};
            stmt.accept(new TreeScanner() {
                @Override public void visitBreak(JCTree.JCBreak tree) { found[0] = true; }
                @Override public void visitContinue(JCTree.JCContinue tree) { found[0] = true; }
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) {}
                @Override public void visitForLoop(JCTree.JCForLoop tree) {}
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) {}
            });
            return found[0];
        }

        private boolean bodyContainsLoop(JCTree.JCBlock body) {
            boolean[] found = {false};
            body.accept(new TreeScanner() {
                @Override public void visitForLoop(JCTree.JCForLoop tree) { found[0] = true; }
                @Override public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) { found[0] = true; }
                @Override public void visitWhileLoop(JCTree.JCWhileLoop tree) { found[0] = true; }
                @Override public void visitDoLoop(JCTree.JCDoWhileLoop tree) { found[0] = true; }
            });
            return found[0];
        }

        private String freshLabel(String prefix) {
            return prefix + "_" + (labelCounter++);
        }

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
            String methodName = qualifiedMethodName(method.sym);

            List<Parameter> inputs = new ArrayList<>();
            for (var param : method.params) {
                inputs.add(new Parameter(ident(param.name.toString()), node(translateType(param.type, param.mods))));
            }

            List<Parameter> outputs = new ArrayList<>();
            if (method.restype != null && method.restype.type != null
                    && method.restype.type.getTag() != TypeTag.VOID) {
                outputs.add(new Parameter(ident(LAUREL_RESULT_BINDING), node(translateType(method.restype.type))));
            }

            List<Condition> preconditions = new ArrayList<>();
            List<Condition> postconditions = new ArrayList<>();
            StmtExpr methodBody = null;

            if (method.body != null) {
                MethodOrLoopContract contract = contractCompiler.getContract(method.body);
                for (var pre : contract.preconditions()) {
                    var preExpr = pre.get();
                    StmtExpr converted = (preExpr instanceof JCTree.JCLambda lambda)
                            ? convertLambdaBody(lambda, Map.of())
                            : convertExpression(preExpr);
                    preconditions.add(new Condition(node(converted), null, false));
                }
                for (var post : contract.postconditions()) {
                    var postExpr = post.get();
                    if (postExpr instanceof JCTree.JCLambda lambda) {
                        Map<String, String> renames = lambda.params.size() == 1
                                ? Map.of(lambda.params.getFirst().name.toString(), LAUREL_RESULT_BINDING)
                                : Map.of();
                        postconditions.add(new Condition(node(convertLambdaBody(lambda, renames)), null, false));
                    } else {
                        postconditions.add(new Condition(node(convertExpression(postExpr)), null, false));
                    }
                }

                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(method.body);
                if (!implStatements.isEmpty()) {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var statement : implStatements) {
                        StmtExpr converted = convertStatement(statement);
                        if (converted != null) {
                            stmts.add(node(converted));
                        }
                    }
                    methodBody = new StmtExpr.Block(stmts, null);
                }
            }

            boolean isPure = jverifyUtils.isPure(method.sym);
            if (isPure && method.body != null && methodBody != null
                    && bodyContainsLoop(method.body)) {
                if (!postconditions.isEmpty()) {
                    throw new JavaViolationException(
                        "@Pure function with a loop and a postcondition is not yet supported");
                }
                methodBody = null;
            }

            Body body;
            boolean canStayTransparent = isPure && postconditions.isEmpty();
            if (methodBody == null) {
                if (postconditions.isEmpty()) {
                    body = new Body.External();
                } else {
                    body = new Body.Abstract(postconditions);
                }
            } else if (canStayTransparent) {
                body = new Body.Transparent(node(methodBody));
            } else {
                body = new Body.Opaque(postconditions, node(methodBody), List.of());
            }

            var proc = new Procedure(
                    ident(methodName), inputs, outputs, preconditions,
                    null, isPure, body, null, List.of());
            procedures.add(proc);
        }

        private StmtExpr emitLoop(StmtExpr cond, List<AstNode<StmtExpr>> invariants, StmtExpr loopBody,
                                  StmtExpr step, List<StmtExpr> preamble,
                                  String breakLbl, String continueLbl) {
            if (breakLbl != null) {
                StmtExpr wrappedBody = new StmtExpr.Block(List.of(node(loopBody)), continueLbl);
                StmtExpr whileBody;
                if (step != null) {
                    whileBody = new StmtExpr.Block(List.of(node(wrappedBody), node(step)), null);
                } else {
                    whileBody = wrappedBody;
                }
                StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, null, node(whileBody), false);
                List<AstNode<StmtExpr>> outerStmts = new ArrayList<>();
                for (var p : preamble) outerStmts.add(node(p));
                outerStmts.add(node(whileNode));
                return new StmtExpr.Block(outerStmts, breakLbl);
            } else {
                StmtExpr whileNode = new StmtExpr.While(node(cond), invariants, null, node(loopBody), false);
                if (preamble.isEmpty()) {
                    return whileNode;
                }
                List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                for (var p : preamble) stmts.add(node(p));
                stmts.add(node(whileNode));
                return new StmtExpr.Block(stmts, null);
            }
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            return convertStatement(statement, Map.of());
        }

        private StmtExpr convertStatementOrEmpty(JCTree.JCStatement statement, Map<String, String> renames) {
            StmtExpr converted = convertStatement(statement, renames);
            return converted != null ? converted : new StmtExpr.Block(List.of(), null);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement, Map<String, String> renames) {
            return switch (statement) {
                case JCTree.JCAssert assertStmt ->
                        new StmtExpr.Assert(new Condition(node(convertExpression(assertStmt.cond, renames)), null, false));
                case JCTree.JCExpressionStatement exprStmt -> convertExpression(exprStmt.expr, renames);
                case JCTree.JCBlock blk -> convertBlock(blk, renames);
                case JCTree.JCVariableDecl varDecl -> {
                    HighType type = translateType(varDecl.type, varDecl.mods);
                    var param = new Parameter(ident(varDecl.name.toString()), node(type));
                    var decl = new Variable.Declare(param);
                    if (varDecl.init != null) {
                        yield new StmtExpr.Assign(
                            List.of(nodeVar(decl)),
                            node(convertExpression(varDecl.init, renames)));
                    } else {
                        yield new StmtExpr.Var(decl);
                    }
                }
                case JCTree.JCIf ifStmt -> {
                    StmtExpr cond = convertExpression(ifStmt.cond, renames);
                    StmtExpr thenBranch = convertStatementOrEmpty(ifStmt.thenpart, renames);
                    AstNode<StmtExpr> elseNode = null;
                    if (ifStmt.elsepart != null) {
                        StmtExpr elseStmt = convertStatement(ifStmt.elsepart, renames);
                        if (elseStmt != null) {
                            elseNode = node(elseStmt);
                        }
                    }
                    yield new StmtExpr.IfThenElse(node(cond), node(thenBranch), elseNode);
                }
                case JCTree.JCReturn retStmt -> {
                    AstNode<StmtExpr> value = retStmt.expr != null ? node(convertExpression(retStmt.expr, renames)) : null;
                    yield new StmtExpr.Return(value);
                }
                case JCTree.JCWhileLoop whileStmt -> {
                    yield withLoopLabels(whileStmt.body, (breakLbl, continueLbl) -> {
                        StmtExpr cond = convertExpression(whileStmt.cond, renames);
                        var parts = extractLoopParts(whileStmt.body, renames);
                        return emitLoop(cond, parts.invariants, parts.body, null, List.of(),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCForLoop forLoop -> {
                    if (forLoop.init.size() > 1 || forLoop.step.size() > 1)
                        throw new JavaViolationException("Multi-init or multi-step for loops are not supported");
                    yield withLoopLabels(forLoop.body, (breakLbl, continueLbl) -> {
                        StmtExpr init = forLoop.init.isEmpty() ? new StmtExpr.Block(List.of(), null)
                                : convertStatement(forLoop.init.getFirst(), renames);
                        StmtExpr cond = forLoop.cond != null
                                ? convertExpression(forLoop.cond, renames)
                                : new StmtExpr.LiteralBool(true);
                        StmtExpr step = forLoop.step.isEmpty() ? new StmtExpr.Block(List.of(), null)
                                : convertStatement(forLoop.step.getFirst(), renames);
                        var parts = extractLoopParts(forLoop.body, renames);
                        return emitLoop(cond, parts.invariants, parts.body, step, List.of(init),
                                breakLbl, continueLbl);
                    });
                }
                case JCTree.JCDoWhileLoop doWhile -> {
                    var breakLbl = freshLabel("loop_break");
                    var continueLbl = freshLabel("loop_continue");
                    var javaLabel = pendingLabel;
                    pendingLabel = null;
                    labelStack.push(new LabelEntry(javaLabel, breakLbl, continueLbl));
                    try {
                        var cond = convertExpression(doWhile.cond, renames);
                        var parts = extractLoopParts(doWhile.body, renames);
                        var notCond = primitiveOp(new Operation.Not(), node(cond));
                        var exitIfDone = new StmtExpr.IfThenElse(node(notCond),
                                node(new StmtExpr.Exit(breakLbl)), null);
                        var wrappedBody = new StmtExpr.Block(List.of(node(parts.body)), continueLbl);
                        var whileBody = new StmtExpr.Block(List.of(node(wrappedBody), node(exitIfDone)), null);
                        var whileNode = new StmtExpr.While(node(new StmtExpr.LiteralBool(true)), parts.invariants, null, node(whileBody), false);
                        yield new StmtExpr.Block(List.of(node(whileNode)), breakLbl);
                    } finally {
                        labelStack.pop();
                    }
                }
                case JCTree.JCBreak breakStmt -> {
                    yield new StmtExpr.Exit(resolveBreakLabel(breakStmt));
                }
                case JCTree.JCContinue contStmt -> {
                    yield new StmtExpr.Exit(resolveContinueLabel(contStmt));
                }
                case JCTree.JCLabeledStatement labeledStmt -> {
                    pendingLabel = labeledStmt.label.toString();
                    if (labeledStmt.body instanceof JCTree.JCWhileLoop
                            || labeledStmt.body instanceof JCTree.JCForLoop
                            || labeledStmt.body instanceof JCTree.JCDoWhileLoop) {
                        yield convertStatement(labeledStmt.body, renames);
                    } else {
                        String brkLbl = freshLabel("label_break");
                        labelStack.push(new LabelEntry(pendingLabel, brkLbl, null));
                        pendingLabel = null;
                        try {
                            StmtExpr body = convertStatementOrEmpty(labeledStmt.body, renames);
                            yield new StmtExpr.Block(List.of(node(body)), brkLbl);
                        } finally {
                            labelStack.pop();
                        }
                    }
                }
                case JCTree.JCSkip ignored -> null;
                default -> throw new JavaViolationException("Unsupported statement: " + statement.getClass().getSimpleName());
            };
        }

        private StmtExpr convertBlock(JCTree.JCBlock blk, Map<String, String> renames) {
            List<AstNode<StmtExpr>> statements = new ArrayList<>();
            for (var statement : blk.stats) {
                StmtExpr converted = convertStatement(statement, renames);
                if (converted != null) {
                    statements.add(node(converted));
                }
            }
            return new StmtExpr.Block(statements, null);
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

        private record LoopParts(List<AstNode<StmtExpr>> invariants, StmtExpr body) {}

        private LoopParts extractLoopParts(JCTree.JCStatement body, Map<String, String> renames) {
            if (body instanceof JCTree.JCBlock loopBlock) {
                if (!MethodOrLoopContractCompiler.hasContractStructure(loopBlock)) {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var s : loopBlock.getStatements()) {
                        StmtExpr converted = convertStatement(s, renames);
                        if (converted != null) stmts.add(node(converted));
                    }
                    return new LoopParts(List.of(), new StmtExpr.Block(stmts, null));
                }
                MethodOrLoopContract loopContract = contractCompiler.getContract(loopBlock);
                List<AstNode<StmtExpr>> invariants = new ArrayList<>();
                for (var inv : loopContract.loopInvariants()) {
                    invariants.add(node(convertExpression(inv.get(), renames)));
                }
                var implStatements = MethodOrLoopContractCompiler.getImplementationStatements(loopBlock);
                List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                for (var s : implStatements) {
                    StmtExpr converted = convertStatement(s, renames);
                    if (converted != null) stmts.add(node(converted));
                }
                return new LoopParts(invariants, new StmtExpr.Block(stmts, null));
            } else {
                return new LoopParts(List.of(), convertStatementOrEmpty(body, renames));
            }
        }

        private Variable convertVariable(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield new Variable.Local(ident(renames.getOrDefault(name, name)));
                }
                case JCTree.JCFieldAccess fa ->
                    new Variable.Field(node(convertExpression(fa.selected, renames)), ident(fa.name.toString()));
                case JCTree.JCParens parens -> convertVariable(parens.expr, renames);
                default -> throw new JavaViolationException("Unsupported variable expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr, Map<String, String> renames) {
            return switch (expr) {
                case JCTree.JCLiteral literal -> convertLiteral(literal);
                case JCTree.JCIdent ident -> {
                    String name = ident.name.toString();
                    yield var_(renames.getOrDefault(name, name));
                }
                case JCTree.JCParens parens ->
                        convertExpression(parens.expr, renames);
                case JCTree.JCBinary binary -> convertBinary(binary, renames);
                case JCTree.JCUnary unary -> convertUnary(unary, renames);
                case JCTree.JCAssign asgn ->
                        new StmtExpr.Assign(List.of(nodeVar(convertVariable(asgn.lhs, renames))),
                                node(convertExpression(asgn.rhs, renames)));
                case JCTree.JCConditional cond ->
                        new StmtExpr.IfThenElse(node(convertExpression(cond.cond, renames)),
                                node(convertExpression(cond.truepart, renames)),
                                node(convertExpression(cond.falsepart, renames)));
                case JCTree.JCMethodInvocation invocation -> {
                    var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        yield convertJVerifyCall(invocation, jverifyMethod, renames);
                    }
                    var methodSym = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    String calleeName = qualifiedMethodName(methodSym);
                    List<AstNode<StmtExpr>> args = new ArrayList<>();
                    for (var arg : invocation.args) {
                        args.add(node(convertExpression(arg, renames)));
                    }
                    yield new StmtExpr.StaticCall(ident(calleeName), args);
                }
                case JCTree.LetExpr letExpr -> {
                    List<AstNode<StmtExpr>> stmts = new ArrayList<>();
                    for (var def : letExpr.defs) {
                        StmtExpr converted = convertStatement(def, renames);
                        if (converted != null) stmts.add(node(converted));
                    }
                    stmts.add(node(convertExpression(letExpr.expr, renames)));
                    yield new StmtExpr.Block(stmts, null);
                }
                case JCTree.JCFieldAccess fa when fa.type.constValue() != null ->
                    convertConstantValue(fa.type.getTag(), fa.type.constValue());
                case JCTree.JCNewClass newClass -> {
                    String name = newClass.type.tsym
                            .getQualifiedName().toString()
                            .replace('$', '.');
                    referencedCompositeTypes.add(name);
                    for (var arg : newClass.args) {
                        convertExpression(arg, renames);
                    }
                    yield new StmtExpr.New(ident(name));
                }
                case JCTree.JCInstanceOf instanceOf -> {
                    throw new JavaViolationException(
                        "instanceof on opaque reference types is not yet supported");
                }
                default -> throw new JavaViolationException("Unsupported expression: " + expr.getClass().getSimpleName());
            };
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return convertConstantValue(literal.typetag, literal.value);
        }

        private StmtExpr convertConstantValue(TypeTag tag, Object v) {
            return switch (tag) {
                case BOOLEAN -> new StmtExpr.LiteralBool(((Number) v).intValue() != 0);
                case CHAR, INT, SHORT, BYTE, LONG -> longLiteral(((Number) v).longValue());
                default -> throw new JavaViolationException("Unsupported constant type tag: " + tag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary, Map<String, String> renames) {
            AstNode<StmtExpr> lhs = node(convertExpression(binary.lhs, renames));
            AstNode<StmtExpr> rhs = node(convertExpression(binary.rhs, renames));
            return switch (binary.getTag()) {
                case PLUS -> primitiveOp(new Operation.Add(), lhs, rhs);
                case MINUS -> primitiveOp(new Operation.Sub(), lhs, rhs);
                case MUL -> primitiveOp(new Operation.Mul(), lhs, rhs);
                case DIV -> primitiveOp(new Operation.DivT(), lhs, rhs);
                case MOD -> primitiveOp(new Operation.ModT(), lhs, rhs);
                case EQ -> primitiveOp(new Operation.Eq(), lhs, rhs);
                case NE -> primitiveOp(new Operation.Neq(), lhs, rhs);
                case GT -> primitiveOp(new Operation.Gt(), lhs, rhs);
                case LT -> primitiveOp(new Operation.Lt(), lhs, rhs);
                case GE -> primitiveOp(new Operation.Geq(), lhs, rhs);
                case LE -> primitiveOp(new Operation.Leq(), lhs, rhs);
                case AND -> primitiveOp(new Operation.AndThen(), lhs, rhs);
                case OR -> primitiveOp(new Operation.OrElse(), lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary op: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary, Map<String, String> renames) {
            AstNode<StmtExpr> inner = node(convertExpression(unary.arg, renames));
            return switch (unary.getTag()) {
                case NOT -> primitiveOp(new Operation.Not(), inner);
                case NEG -> primitiveOp(new Operation.Neg(), inner);
                case PREINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case PREDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Pre(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTINC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Incr(), nodeVar(convertVariable(unary.arg, renames)));
                case POSTDEC -> new StmtExpr.IncrDecr(new IncrDecrMode.Post(), new IncrDecrOp.Decr(), nodeVar(convertVariable(unary.arg, renames)));
                default -> throw new JavaViolationException("Unsupported unary op: " + unary.getTag());
            };
        }

        private StmtExpr convertJVerifyCall(JCTree.JCMethodInvocation invocation,
                                            Symbol.MethodSymbol jverifyMethod,
                                            Map<String, String> renames) {
            var name = jverifyMethod.getQualifiedName().toString();
            return switch (name) {
                case "check" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("check should have a single argument");
                    yield new StmtExpr.Assert(new Condition(node(convertExpression(invocation.args.getFirst(), renames)), null, false));
                }
                case "assume" -> {
                    if (invocation.args.size() != 1)
                        throw new JavaViolationException("assume should have a single argument");
                    yield new StmtExpr.Assume(node(convertExpression(invocation.args.getFirst(), renames)));
                }
                case "implies" -> {
                    if (invocation.args.size() != 2)
                        throw new JavaViolationException("implies should have two arguments");
                    var notA = primitiveOp(new Operation.Not(), node(convertExpression(invocation.args.get(0), renames)));
                    yield primitiveOp(new Operation.Or(), node(notA), node(convertExpression(invocation.args.get(1), renames)));
                }
                case "forall", "exists" -> {
                    if (invocation.args.size() != 1 || !(invocation.args.getFirst() instanceof JCTree.JCLambda lambda))
                        throw new JavaViolationException(name + " requires a single lambda argument");
                    StmtExpr qBody = convertLambdaBody(lambda, renames);
                    for (int i = lambda.params.size() - 1; i >= 0; i--) {
                        var p = lambda.params.get(i);
                        var ty = translateType(p.type, p.mods);
                        var param = new Parameter(ident(p.name.toString()), node(ty));
                        var mode = name.equals("forall")
                                ? (QuantifierMode) new QuantifierMode.Forall()
                                : new QuantifierMode.Exists();
                        qBody = new StmtExpr.Quantifier(mode, param, null, node(qBody));
                    }
                    yield qBody;
                }
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }
    }
}

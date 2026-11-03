package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.IncrDecrMode;
import org.strata.jverify.laurel.IncrDecrOp;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.strata.java.bytecode.frontend.ClassfileQueries;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.CrtRange;
import org.strata.java.bytecode.frontend.Locals;
import org.strata.java.bytecode.frontend.MethodBodyFactory;
import org.strata.java.bytecode.frontend.Span;
import org.strata.java.bytecode.frontend.StructException;

/**
 * The bytecode-to-Laurel expression layer: an operand-stack simulation over
 * a method's instructions whose stack holds Laurel {@code AstNode<StmtExpr>}
 * directly. Every translation decision — including refusals and their exact
 * message texts — is pinned by the corpus (which asserts byte-identical
 * diagnostics) and applied at the corresponding instruction.
 * Invocations, JVerify spec calls, and field accesses are delegated to
 * {@link InvokeTranslator}, {@link SpecCallTranslator}, and
 * {@link FieldAccessTranslator}, which share this body's stack and side
 * tables.
 *
 * <p><b>Range discipline.</b> Every sub-expression of a statement carries
 * the statement's CRT span (line:column). Expressions are built bottom-up
 * before their owning statement is known, so nodes carry UNKNOWN_RANGE until
 * {@link #addStmt}, which computes the statement's range (its CRT span; see
 * {@link #stmtRange}) and restamps the subtree via
 * {@link TranslationContext#placeStatement}.
 *
 * <p><b>Deferred refusals.</b> Some bytecode shapes are refused only when
 * they reach a statement or condition position (bitwise operators,
 * float/double literals, an unconsumed three-way compare, a lambda in a
 * non-spec position); mid-expression they may still be consumed by a
 * recognizing peephole. Such shapes become marker nodes, and
 * {@link #checkPlaceable} throws the pinned refusal message when a marked
 * node survives into a placed statement or condition.
 */
final class BodyTranslator {

    final TranslationContext ctx;

    // --- Operand stack + emitted statements ---
    final Deque<AstNode<StmtExpr>> stack = new ArrayDeque<>();
    final List<AstNode<StmtExpr>> stmts = new ArrayList<>();
    /** Statement -> the seq index it was emitted at ({@link #addStmtAt}), for loop-condition attribution. */
    final Map<AstNode<StmtExpr>, Integer> stmtSeq = new IdentityHashMap<>();

    // --- Variable bookkeeping ---
    final Set<String> declared;              // variable keys already declared (slot@scopeStart, or v<slot>)
    final Map<Integer, String> slotName, slotDesc;
    Locals locals;                // scoped local table (nullable); resolved at `pos`
    /** Hoisted-declaration names by declaration identity ({@code Locals.slotVarKey}); see {@code MethodBody.HoistedDecl}. */
    Map<String, String> hoistedNames = Map.of();
    int pos = -1;                            // current seq-index, for scoped local resolution
    ClassModel cm;                           // enclosing class, for resolving lambda impl methods
    List<CrtRange> crt = List.of();
    org.strata.java.bytecode.frontend.LineNumbers lines;   // position fallback when crt is empty
    int[] seqToBci;
    boolean inCtor;
    boolean isInstance;                      // instance method: slot 0 is `this` (javac never stores to it)
    boolean dropSuper;
    String retDesc;
    final Map<Integer, String> slotAlias = new HashMap<>();  // slot -> minted name for an unnamed local
    /**
     * While a catch handler is translated: its binding's slot -> the binding and the
     * arm's type. Loads of the binding read it at the arm's type ({@code e as E}):
     * Laurel types it at the join of what the {@code try} body throws, which an
     * implicit throw made explicit can widen past the arm. The guard discharges the
     * cast. It is made at the load, not by rewriting the handler afterwards, because
     * the side tables are keyed on node identity.
     */
    final Map<Integer, CatchCast> catchCasts = new HashMap<>();

    record CatchCast(String binding, AstNode<HighType> armType) {
    }
    final Map<Integer, String> slotType = new HashMap<>();   // slot -> descriptor it currently holds
    int synthCounter;
    Set<Integer> loadedSlots = Set.of();
    /** Reference locals some store fills with a value read from the heap ({@link #heapReadSlots}). */
    Set<String> heapReadSlots = Set.of();
    int pendingIncSlot = -1;                 // deferred mid-expression iinc (prefix ++x)
    int pendingIncDelta;
    Map<Integer, List<java.lang.classfile.Annotation>> slotTypeAnnos = Map.of();
    List<org.strata.java.bytecode.frontend.MethodBody.SlotRange> impureLocals = List.of();

    // --- Laurel-side side tables (identity-keyed on the nodes this layer built) ---
    /** Pushed value's JVM descriptor where known (boolean/int erasure oracle). */
    final Map<AstNode<StmtExpr>, String> descOf = new IdentityHashMap<>();
    /** Values known to be boolean-typed. */
    final Set<AstNode<StmtExpr>> bools = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Values typed by a type variable in scope: loads of a local whose
     * (LVTT) type is a type parameter, and calls whose generic result is a
     * type variable (their erased descriptor says {@code Object}).
     */
    final Set<AstNode<StmtExpr>> typeVarValues = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Reference values that may be null although their Laurel type is a
     * composite: a field read, a load of a {@linkplain #heapReadSlots local}
     * filled from the heap, a cast of either, and a conditional expression with
     * such an arm (ControlFlowStructurer.buildTernary). The box model refuses to
     * unbox them (InvokeTranslator.boxMember), as a Laurel composite value is
     * never null where Java's may be.
     */
    final Set<AstNode<StmtExpr>> heapReadValues = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Loads of a local declared {@code @Impure} (a local or parameter type
     * annotation, or a type variable with an {@code @Impure} bound): a heap
     * object, so {@code ==} on it is a reference comparison.
     */
    final Set<AstNode<StmtExpr>> impureValues = Collections.newSetFromMap(new IdentityHashMap<>());
    /** {@code String} constants pushed by {@code ldc} (markers never enter). */
    final Set<AstNode<StmtExpr>> stringLiterals = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Results of string concatenation ({@code invokedynamic makeConcatWithConstants}). */
    final Set<AstNode<StmtExpr>> stringConcatResults = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 3-way compare result -> its two operands (for rebuilding `a <op> b`). */
    final Map<AstNode<StmtExpr>, AstNode<StmtExpr>[]> cmp3 = new IdentityHashMap<>();
    final Map<AstNode<StmtExpr>, Boolean> cmp3NanHigh = new IdentityHashMap<>();
    /** NEW-instruction marker -> internal class name (bridges NEW..invokespecial). */
    final Map<AstNode<StmtExpr>, String> pendingNew = new IdentityHashMap<>();
    /**
     * The receiver-typing oracle: a value's declared type in its SOURCE-STYLE
     * rendering (simple names, generics rendered), where one is recoverable
     * (locals via the LVT, casts, allocations, this). Used by the call
     * layer's receiver resolution — deliberately blind to library types'
     * packages (simple-name resolution).
     */
    final Map<AstNode<StmtExpr>, TypeRef> staticTypeOf = new IdentityHashMap<>();
    /**
     * Deferred refusals: consuming this node into a placed statement or
     * condition throws a JavaViolationException with the corpus-pinned
     * refusal message.
     */
    final Map<AstNode<StmtExpr>, String> deferredRefusal = new IdentityHashMap<>();
    /**
     * Lambda / method-reference handles produced by invokedynamic: consumed
     * specially by spec calls and `return`; anywhere else refuses with the
     * JCLambda message.
     */
    final Map<AstNode<StmtExpr>, LambdaHandle> lambdas = new IdentityHashMap<>();

    /** An invokedynamic-produced closure: the synthetic impl (lambda) or referenced method. */
    record LambdaHandle(String implName, String implDesc, int captureCount, boolean isMethodRef,
                        String samFqn, String implOwner, boolean implStatic,
                        String samName, String samDesc, String instantiatedDesc) {
    }

    /**
     * A lambda used as a VALUE -- passed to a method, or stored -- lowers to an
     * allocation of a fresh composite that extends the functional interface
     * ({@code new Enclosing.lambda$m$0}). A call through the interface reasons
     * from the interface's own contract, so the lambda's body is checked against
     * that contract: the sort is queued in {@link TranslationContext#pendingLambdas}
     * and the driver translates the synthetic impl method as the sort's override
     * of the interface method (BytecodeToLaurelCompiler.compileLambda). The sort is
     * named after the synthetic impl method, qualified by the class that declares
     * it, so two classes whose lambdas sit at the same place cannot share a sort
     * (SameLineAndColumnLambdaA/B).
     *
     * <p>Left as the handle -- and refused where it is placed (corpus-pinned message) --
     * for a method reference, and for a functional interface that is a generic
     * class of this program, whose instantiation this sort could not name.
     */
    AstNode<StmtExpr> materializeLambda(AstNode<StmtExpr> handleNode) {
        LambdaHandle h = lambdas.get(handleNode);
        if (h == null || h.isMethodRef() || h.samFqn() == null || ctx.currentClassFqn == null
                || !ctx.resolver.classTypeParams(h.samFqn()).isEmpty()) {
            return handleNode;
        }
        String sort = ctx.currentClassFqn + "." + h.implName();
        // The interface's sort is named the way every other use of that type names
        // it -- a program interface by its fqn, a library one by its SHORT name
        // (`IntPredicate`), see TypeRef.toLaurel -- or the lambda's `extends` edge
        // would name a different sort from the parameter it is passed to.
        TypeRef samType = TypeRef.ofDescriptor("L" + h.samFqn().replace('.', '/') + ";");
        String samSort = ctx.resolver.programClassModels.containsKey(h.samFqn())
                ? CompositeModel.sortNameForFqn(h.samFqn())
                : ctx.resolver.qualifyTypeName(samType.nameWithScope(), samType.fqnFromContext());
        ctx.composites.lambdaSupertypes.put(sort, List.of(samSort));
        ctx.composites.referencedCompositeTypes.add(sort);
        ctx.composites.referencedCompositeTypes.add(samSort);
        if (ctx.queuedLambdaSorts.add(sort)) {
            ctx.pendingLambdas.add(new TranslationContext.PendingLambda(sort, samSort, h));
        }
        AstNode<StmtExpr> nw = e(new StmtExpr.New(LaurelFactory.ident(sort), List.of()));
        callNodes.add(nw);
        staticTypeOf.put(nw, samType);
        return nw;
    }

    /**
     * Lambdas hidden behind a functional-interface CHECKCAST: the marker
     * refuses as JCLambda where a bare lambda is required, but throwsOn case
     * postconditions unwrap the cast.
     */
    final Map<AstNode<StmtExpr>, LambdaHandle> castWrappedLambdas = new IdentityHashMap<>();

    // --- Array bookkeeping (used only while the array model is enabled) ---
    /** Names of the locals minted for array allocations ({@link #mintTemp}). */
    final Set<String> allocTemps = new java.util.HashSet<>();
    /** Loads of a local with no LVT scope at the load (selects UNTYPED_LOCAL over UNKNOWN_ELEMENT). */
    final Set<AstNode<StmtExpr>> untypedLoads = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Nesting depth of contexts whose statements would not run exactly once,
     * in order, per Java evaluation (conditional arms, a pre-tested loop
     * header, a latch-tested loop condition, a boolean region past its first
     * test): array allocations and writes refuse inside them.
     */
    private int conditionalDepth;

    // --- The per-concern translators sharing this body's stack and side tables ---
    final InvokeTranslator invokes;
    final SpecCallTranslator specCalls;
    final FieldAccessTranslator fieldAccess;
    final ArrayAccessTranslator arrays;

    BodyTranslator(TranslationContext ctx, Map<Integer, String> slotName, Map<Integer, String> slotDesc,
               Set<String> declared) {
        this.ctx = ctx;
        this.slotName = slotName;
        this.slotDesc = slotDesc;
        this.declared = new java.util.HashSet<>(declared);
        this.invokes = new InvokeTranslator(this);
        this.specCalls = new SpecCallTranslator(this);
        this.fieldAccess = new FieldAccessTranslator(this);
        this.arrays = new ArrayAccessTranslator(this);
    }

    /** Enter a conditional or repeated context; pair with {@link #exitConditional} in a {@code finally}. */
    void enterConditional() {
        conditionalDepth++;
    }

    void exitConditional() {
        conditionalDepth--;
    }

    boolean inConditional() {
        return conditionalDepth > 0;
    }

    /** A fresh local name from the minted-name counter ({@code t$<n>}), tied to no slot. */
    String mintTemp() {
        return "t$" + (++synthCounter);
    }

    /**
     * True iff evaluating {@code n} has no effect and reads no heap: a
     * local, a literal, a pending {@code new} marker, or a primitive
     * operator over such operands. Array statements may be emitted while only
     * such values are pending on the stack, because no statement can change
     * them.
     */
    boolean isSimple(AstNode<StmtExpr> n) {
        return switch (n.val()) {
            case StmtExpr.Var v -> v.var_() instanceof Variable.Local;
            case StmtExpr.LiteralInt i -> true;
            case StmtExpr.LiteralBool lb -> true;
            case StmtExpr.LiteralString s -> pendingNew.containsKey(n);
            case StmtExpr.StaticCall c -> LaurelFactory.isOperationProcName(c.callee().text())
                    && c.arguments().stream().allMatch(this::isSimple);
            default -> false;
        };
    }

    /**
     * Refuse a specification argument that mentions an array allocation's
     * local: the allocation ran as body statements before the specification
     * was recognized.
     */
    void refuseAllocTempMention(AstNode<StmtExpr> n) {
        if (allocTemps.isEmpty() || n == null) {
            return;
        }
        if (n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local l
                && allocTemps.contains(l.name().text())) {
            throw new JavaViolationException(ArrayModel.SPEC_WRITE);
        }
        for (AstNode<StmtExpr> child : childrenOf(n.val())) {
            refuseAllocTempMention(child);
        }
    }

    /**
     * Refuse {@code old(...)} inside {@code n}. A precondition and a throwsOn
     * guard are already evaluated in the pre-state, so {@code old} there has
     * no meaning. Strata rejects it too, but as a resolution error that stops
     * verification of the whole file; refusing here skips only this method.
     */
    static void refuseOld(AstNode<StmtExpr> n, String where) {
        if (n == null) {
            return;
        }
        if (n.val() instanceof StmtExpr.Old) {
            throw new JavaViolationException("old(...) is not allowed in " + where
                    + ": it is already evaluated in the method's pre-state");
        }
        for (AstNode<StmtExpr> child : childrenOf(n.val())) {
            refuseOld(child, where);
        }
    }

    // --- Delegates for the translators' externally-used entry points ---

    AstNode<StmtExpr> translateLambdaBody(LambdaHandle lambda, Map<String, String> extraRenames) {
        return specCalls.translateLambdaBody(lambda, extraRenames);
    }

    List<LambdaBinder> lambdaBinders(LambdaHandle lambda) {
        return specCalls.lambdaBinders(lambda);
    }

    // --- Node construction (provisional ranges; restamped at placement) ---

    AstNode<StmtExpr> e(StmtExpr v) {
        return new AstNode<>(v, SourceMap.UNKNOWN_RANGE);
    }

    /**
     * A negative literal renders as {@code $neg(<abs>)} whose inner literal
     * stays deliberately range-less: pinned in the rerange stop set so
     * statement placement leaves it untouched.
     */
    StmtExpr pinnedLongLiteral(long v) {
        StmtExpr lit = LaurelFactory.longLiteral(v);
        if (lit instanceof StmtExpr.StaticCall sc) {
            ctx.method.statementNodes.add(sc.arguments().get(0));
        }
        return lit;
    }

    AstNode<StmtExpr> push(StmtExpr v) {
        AstNode<StmtExpr> n = e(v);
        stack.push(n);
        return n;
    }

    void noteDesc(AstNode<StmtExpr> n, String desc) {
        if (desc == null) {
            return;
        }
        descOf.put(n, desc);
        if (desc.equals("Z")) {
            bools.add(n);
        }
    }

    // --- Spans and statement placement ---

    Span spanAt(int seqIdx) {
        return MethodBodyFactory.spanOf(crt, seqToBci, seqIdx);
    }

    /**
     * Position fallback for input compiled without {@code -Xjcov}: a point at
     * (line, 1) for the instruction's LineNumberTable line, else the method's
     * default range. Never taken when a CRT exists ({@code crt.isEmpty()}
     * gate), so FULL-tier stamping is untouched.
     */
    private FileRange lineFallbackRange(int seqIdx) {
        if (crt.isEmpty() && lines != null
                && seqToBci != null && seqIdx >= 0 && seqIdx < seqToBci.length) {
            int line = lines.lineAt(seqToBci[seqIdx]);
            if (line > 0) {
                FileRange r = ctx.src.lineRange(line);
                if (r != SourceMap.UNKNOWN_RANGE) {
                    return r;
                }
            }
        }
        return ctx.src.defaultRange;
    }

    /**
    /**
     * The statement's FileRange: the CRT statement span of the instruction at
     * {@code seqIdx} (its bytecode-native line:column), or the context's
     * current default range when no span is known. CRT spans are
     * statement-level -- the end includes the trailing ';' and there is no
     * source to trim it, so the span stands as-is.
     */
    FileRange stmtRange(int seqIdx, boolean containsLambda) {
        Span span = spanAt(seqIdx);
        if (span.isNone()) {
            return lineFallbackRange(seqIdx);
        }
        int beginLine = span.beginLine();
        int beginCol = span.beginCol();
        // A statement that materializes a lambda carries the invokedynamic's
        // CRT window, which starts at the class declaration rather than the
        // statement; with no source to find the first non-blank column, anchor
        // at the start of the span's last line instead.
        if (span.beginLine() != span.endLine() && containsLambda) {
            beginLine = span.endLine();
            beginCol = 1;
        } else if (lines != null && seqToBci != null && seqIdx >= 0 && seqIdx < seqToBci.length) {
            int lnt = lines.lineAt(seqToBci[seqIdx]);
            if (lnt > beginLine && lnt <= span.endLine() && !lines.hasLine(span.beginLine())) {
                beginLine = lnt;
                beginCol = 1;
            }
        }
        return ctx.src.rangeOf(beginLine, beginCol, span.endLine(), span.endCol());
    }

    /**
     * Refuse if the tree contains a deferred-refusal marker or a lambda
     * handle in a refused position.
     * Walks the subtree, stopping at already-placed statement nodes.
     */
    void checkPlaceable(AstNode<StmtExpr> n) {
        if (ctx.method.statementNodes.contains(n)) {
            return;
        }
        // POST-order (children first, left to right): the deepest-leftmost
        // refusal wins (a bottom-up order pinned by the corpus).
        for (AstNode<StmtExpr> child : childrenOf(n.val())) {
            checkPlaceable(child);
        }
        refuseNode(n);
    }

    /**
     * A contract argument's combined check, in bottom-up order: per node,
     * children first, then the node's deferred refusal, then the in-contract
     * allocation refusal.
     */
    void checkContractArg(AstNode<StmtExpr> n) {
        if (ctx.method.statementNodes.contains(n)) {
            return;
        }
        for (AstNode<StmtExpr> child : childrenOf(n.val())) {
            checkContractArg(child);
        }
        refuseNode(n);
        if (n.val() instanceof StmtExpr.New) {
            throw new JavaViolationException(
                    "object allocation (including lambdas) inside a contract is not yet supported");
        }
        if (n.val() instanceof StmtExpr.StaticCall c && c.callee().text().startsWith(BoxModel.BOX_HELPER_PREFIX)) {
            throw new JavaViolationException("boxing inside a contract is not yet supported");
        }
    }

    /** The per-node refusal checks shared by the placement/contract walks. */
    private void refuseNode(AstNode<StmtExpr> n) {
        String needle = arrayInitNeedles.get(n);
        if (needle != null && ctx.contractMode) {
            // Library members are never diagnosed: the refusal reaches the
            // callers through the member's refusal reason.
            throw new JavaViolationException("new array with initializers is not supported");
        }
        if (needle != null) {
            // Array creation with initializers: reported at the first
            // element's text, then aborted with ReportedViolation.
            // No sub-statement anchor from bytecode: report at the enclosing
            // statement's range.
            ctx.reporter.reportError(ctx.src.uri, ctx.src.positionRange(ctx.src.defaultRange),
                    "new array with initializers is not supported");
            throw new BytecodeToLaurelCompiler.ReportedViolation();
        }
        String refusal = deferredRefusal.get(n);
        if (refusal != null) {
            throw new JavaViolationException(refusal);
        }
        if (lambdas.containsKey(n)) {
            if (arrays.isArrayConstructorRef(lambdas.get(n))) {
                throw new JavaViolationException(ArrayModel.CTOR_REF);
            }
            // The corpus-pinned refusal for a lambda/method reference in value position.
            throw new JavaViolationException("Unsupported expression: JCLambda");
        }
    }

    static List<AstNode<StmtExpr>> childrenOf(StmtExpr v) {
        var out = new ArrayList<AstNode<StmtExpr>>();
        switch (v) {
            case StmtExpr.IfThenElse n -> {
                out.add(n.cond());
                out.add(n.thenBranch());
                n.elseBranch().ifPresent(out::add);
            }
            case StmtExpr.Block n -> {
                out.addAll(n.invariants());
                n.decreases().ifPresent(out::add);
                out.addAll(n.statements());
            }
            case StmtExpr.While n -> {
                out.add(n.cond());
                out.addAll(n.invariants());
                n.decreases().ifPresent(out::add);
                out.add(n.body());
            }
            case StmtExpr.Return n -> n.values().forEach(out::add);
            case StmtExpr.Var n -> {
                if (n.var_() instanceof Variable.Field f) {
                    out.add(f.target());
                }
            }
            case StmtExpr.Assign n -> {
                for (var t : n.targets()) {
                    if (t.val() instanceof Variable.Field f) {
                        out.add(f.target());
                    }
                }
                out.add(n.value());
            }
            case StmtExpr.IncrDecr n -> {
                if (n.target().val() instanceof Variable.Field f) {
                    out.add(f.target());
                }
            }
            case StmtExpr.CompoundAssign n -> {
                if (n.target().val() instanceof Variable.Field f) {
                    out.add(f.target());
                }
                out.add(n.rhs());
            }
            case StmtExpr.PureFieldUpdate n -> {
                out.add(n.target());
                out.add(n.newValue());
            }
            case StmtExpr.StaticCall n -> out.addAll(n.arguments());
            case StmtExpr.InstanceCall n -> {
                out.add(n.target());
                out.addAll(n.arguments());
            }
            case StmtExpr.ReferenceEquals n -> {
                out.add(n.lhs());
                out.add(n.rhs());
            }
            case StmtExpr.AsType n -> out.add(n.target());
            case StmtExpr.IsType n -> out.add(n.target());
            case StmtExpr.Quantifier n -> {
                n.trigger().ifPresent(out::add);
                out.add(n.body());
            }
            case StmtExpr.Assigned n -> out.add(n.name());
            case StmtExpr.Old n -> out.add(n.value());
            case StmtExpr.Fresh n -> out.add(n.value());
            case StmtExpr.Assert n -> out.add(n.condition());
            case StmtExpr.Assume n -> out.add(n.condition());
            case StmtExpr.Throw n -> out.add(n.value());
            case StmtExpr.Try n -> {
                out.add(n.body());
                for (var c : n.catches()) {
                    c.predicate().ifPresent(out::add);
                    out.add(c.body());
                }
                n.finally_().ifPresent(out::add);
            }
            case StmtExpr.ProveBy n -> {
                out.add(n.value());
                out.add(n.proof());
            }
            case StmtExpr.ContractOf n -> out.add(n.function());
            default -> { }
        }
        return out;
    }

    /** Emit a statement stamped with the current instruction's CRT span range. */
    void addStmt(StmtExpr stmt) {
        addStmtAt(stmt, pos, false);
    }

    void addStmtAt(StmtExpr stmt, int seqIdx, boolean containsLambda) {
        AstNode<StmtExpr> raw = e(stmt);
        FileRange range = stmtRange(seqIdx, containsLambda);
        // The per-statement default range: a refusal reported inside this
        // statement anchors within it (via positionRange(defaultRange)).
        ctx.src.defaultRange = range;
        checkPlaceable(raw);
        for (AstNode<StmtExpr> t : explicitImplicitThrows(raw)) {
            AstNode<StmtExpr> placed = place(t, range);
            stmtSeq.put(placed, seqIdx);
            stmts.add(placed);
        }
        AstNode<StmtExpr> placed = place(raw, range);
        stmtSeq.put(placed, seqIdx);
        stmts.add(placed);
    }

    /**
     * Statement placement wrapper: the rerange copy invalidates the
     * identity-keyed side tables of nodes INSIDE the placed subtree (a void
     * call's node is rebuilt), so the copy's entries are re-keyed by a
     * lockstep walk of the old and new trees.
     */
    AstNode<StmtExpr> place(AstNode<StmtExpr> raw, FileRange range) {
        AstNode<StmtExpr> placed = ctx.placeStatement(raw, range);
        remapIdentity(raw, placed);
        return placed;
    }

    /** Copy identity-keyed side-table entries from a rebuilt subtree's old nodes to the new. */
    void remapIdentity(AstNode<StmtExpr> oldN, AstNode<StmtExpr> newN) {
        if (oldN == newN) {
            return;   // a preserved statement node: subtree untouched
        }
        String key = calleeKeys.get(oldN.val());
        if (key != null) {
            calleeKeys.put(newN.val(), key);
        }
        CalleeRef ref = calleeModels.get(oldN.val());
        if (ref != null) {
            calleeModels.put(newN.val(), ref);
        }
        var oc = childrenOf(oldN.val());
        var nc = childrenOf(newN.val());
        for (int i = 0; i < Math.min(oc.size(), nc.size()); i++) {
            remapIdentity(oc.get(i), nc.get(i));
        }
    }

    // --- Locals: slot naming and typing ---

    String localName(int slot) {
        if (slot == 0 && isInstance) {
            // Structural: in an instance method slot 0 is always `this`
            // (javac never stores to it), so the receiver is recognized even
            // without an LVT naming it.
            return "this";
        }
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) {
                // A hoisted declaration may carry a unique name when sibling
                // scopes reuse the source name; its uses follow it.
                String hoisted = hoistedNames.get(slot + ":" + s.name() + ":" + s.desc());
                return hoisted != null ? hoisted : s.name();
            }
            String alias = slotAlias.get(slot);
            if (alias != null) {
                return alias;
            }
            if (locals.bySlot.containsKey(slot)) {
                return "v" + slot;   // slot named in another scope -> synthetic here
            }
        }
        String alias = slotAlias.get(slot);
        if (alias != null) {
            return alias;
        }
        String nm = slotName.get(slot);
        return nm != null ? nm : "v" + slot;
    }

    String freshName(int slot) {
        String nm = "t$" + (++synthCounter);
        slotAlias.put(slot, nm);
        return nm;
    }

    String localDesc(int slot) {
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) {
                return s.desc();
            }
            if (locals.bySlot.containsKey(slot)) {
                return null;         // type unknown here -> var
            }
        }
        return slotDesc.get(slot);
    }

    /** Declared TypeRef of a local: generic (LVTT) if known, else erased, else null ("var"). */
    TypeRef localTypeRef(int slot) {
        boolean isNat = hasSlotAnno(slot, "Nat");
        boolean isUnbounded = hasSlotAnno(slot, "Unbounded");
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) {
                if (s.genericSig() != null) {
                    return TypeRef.ofSignature(s.genericSig(), isNat, isUnbounded);
                }
                return s.desc() != null ? TypeRef.ofDescriptor(s.desc(), isNat, isUnbounded) : null;
            }
            if (locals.bySlot.containsKey(slot)) {
                return null;         // `var`
            }
        }
        String desc = slotDesc.get(slot);
        return desc != null ? TypeRef.ofDescriptor(desc, isNat, isUnbounded) : null;
    }

    private boolean hasSlotAnno(int slot, String simpleName) {
        var annos = slotTypeAnnos.get(slot);
        if (annos == null) {
            return false;
        }
        String want = "L" + "org/strata/jverify/" + simpleName + ";";
        for (var a : annos) {
            String cn = a.className().stringValue();
            if (cn.equals(want) || cn.endsWith("/" + simpleName + ";")) {
                return true;
            }
        }
        return false;
    }

    String varKey(int slot) {
        String alias = slotAlias.get(slot);
        if (alias != null && (locals == null || locals.at(slot, pos) == null)) {
            return "alias:" + alias;
        }
        return Locals.slotVarKey(locals, slot, pos);
    }

    /** The rename-aware Laurel local for a source name ("this" lowers to self?). */
    AstNode<StmtExpr> localVar(String name) {
        if (name.equals("this")) {
            AstNode<StmtExpr> n = e(LaurelFactory.var_(LaurelFactory.LAUREL_SELF_BINDING));
            staticTypeOf.put(n, thisTypeRef());
            return n;
        }
        return e(LaurelFactory.varForLocal(ctx.method.renames, name));
    }

    private TypeRef thisTypeRef() {
        // The receiver's static type is the enclosing class; render it the
        // simple-name way (it resolves via knownClasses).
        String fqn = ctx.currentClassFqn;
        int dot = fqn == null ? -1 : fqn.lastIndexOf('.');
        String simple = dot >= 0 ? fqn.substring(dot + 1) : fqn;
        return simple == null ? null
                : new TypeRef(TypeRef.Kind.CLASS, simple, simple, simple, false, false, false, fqn);
    }

    // --- Coercions (coerce / coerceTo / coerceArg) ---

    /** boolean target: 0/1 literals become false/true. */
    AstNode<StmtExpr> coerce(AstNode<StmtExpr> v, String desc) {
        if ("Z".equals(desc) && v.val() instanceof StmtExpr.LiteralInt li
                && (li.value() == 0 || li.value() == 1)) {
            AstNode<StmtExpr> b = e(new StmtExpr.LiteralBool(li.value() == 1));
            noteDesc(b, "Z");
            return b;
        }
        return v;
    }

    /** Either direction across the boolean/int erasure. */
    AstNode<StmtExpr> coerceTo(AstNode<StmtExpr> v, String desc) {
        if (desc == null) {
            return v;
        }
        if (desc.equals("Z")) {
            return coerce(v, desc);
        }
        if (isIntLike(desc) && isBool(v)) {
            // int x = <bool>  ->  cond ? 1 : 0 (the restored ternary)
            return e(new StmtExpr.IfThenElse(v,
                    e(new StmtExpr.LiteralInt(1)), Optional.of(e(new StmtExpr.LiteralInt(0)))));
        }
        return v;
    }

    static boolean isIntLike(String desc) {
        return switch (desc) {
            case "I", "S", "B", "C", "J" -> true;
            default -> false;
        };
    }

    boolean isBool(AstNode<StmtExpr> n) {
        return bools.contains(n) || LaurelTrees.isBooleanish(n.val());
    }

    // --- The literal / load / store / increment layer ---

    /** Push a constant as its Laurel literal. */
    void pushConstant(Object v) {
        if (v == null) {
            AstNode<StmtExpr> n = push(nullMarker());
            return;
        }
        switch (v) {
            case Integer i -> noteDesc(push(pinnedLongLiteral(i)), "I");
            case Long l -> noteDesc(push(pinnedLongLiteral(l)), "J");
            case String s -> {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString(s));
                noteDesc(n, "Ljava/lang/String;");
                stringLiterals.add(n);
            }
            case Float f -> defer(push(new StmtExpr.LiteralString("float " + f)),
                    "Unsupported expression: DoubleLiteralExpr");
            case Double d -> defer(push(new StmtExpr.LiteralString("double " + d)),
                    "Unsupported expression: DoubleLiteralExpr");
            case java.lang.constant.ClassDesc cd -> defer(push(new StmtExpr.LiteralString("class")),
                    "Unsupported expression: ClassExpr");
            default -> defer(push(new StmtExpr.LiteralString(String.valueOf(v))),
                    "Unsupported expression: " + v.getClass().getSimpleName());
        }
    }

    /**
     * A bare `null` has no translation: refusal "Unsupported expression:
     * NullLiteralExpr" unless a coercion consumes it (a cast
     * at a call argument). The marker
     * keeps the value distinguishable for coerceArg.
     */
    StmtExpr nullMarker() {
        var n = new StmtExpr.LiteralString("$null");
        return n;
    }

    void defer(AstNode<StmtExpr> n, String message) {
        deferredRefusal.put(n, message);
    }

    /** LOAD: push the named local (applying renames; {@code this} -> self?). */
    void load(LoadInstruction li) {
        if (pendingIncSlot == li.slot()) {
            int c = pendingIncDelta;
            pendingIncSlot = -1;
            AstNode<StmtExpr> n = push(new StmtExpr.IncrDecr(new IncrDecrMode.Pre(),
                    c > 0 ? (IncrDecrOp) new IncrDecrOp.Incr() : new IncrDecrOp.Decr(),
                    localVarTarget(li.slot())));
            noteDesc(n, localDesc(li.slot()));
            return;
        }
        String name = localName(li.slot());
        AstNode<StmtExpr> n = localVar(name);
        CatchCast cast = catchCasts.get(li.slot());
        if (cast != null && cast.binding().equals(name)) {
            n = new AstNode<>(new StmtExpr.AsType(n, cast.armType()), n.source());
        }
        stack.push(n);
        noteDesc(n, localDesc(li.slot()));
        if (heapReadSlots.contains(localKey(locals, li.slot(), pos))) {
            heapReadValues.add(n);
        }
        if (locals != null && locals.at(li.slot(), pos) == null && !name.equals("this")) {
            untypedLoads.add(n);
        }
        TypeRef t = localTypeRef(li.slot());
        if (t != null && !name.equals("this")) {
            staticTypeOf.put(n, t);
        }
        if (t != null && t.kind() == TypeRef.Kind.CLASS && !t.scoped()
                && ctx.typeParams.contains(t.simpleName())) {
            typeVarValues.add(n);
            if (ctx.impureTypeParams.contains(t.simpleName())) {
                impureValues.add(n);
            }
        }
        if (isImpureLocalHere(li.slot())) {
            impureValues.add(n);
        }
    }

    /** Whether the local occupying {@code slot} at the current instruction is declared {@code @Impure}. */
    private boolean isImpureLocalHere(int slot) {
        if (impureLocals.isEmpty() || seqToBci == null || pos < 0 || pos >= seqToBci.length) {
            return false;
        }
        int bci = seqToBci[pos];
        for (var r : impureLocals) {
            if (r.covers(slot, bci)) {
                return true;
            }
        }
        return false;
    }

    private AstNode<Variable> localVarTarget(int slot) {
        String name = localName(slot);
        String laurelName = name.equals("this")
                ? LaurelFactory.LAUREL_SELF_BINDING
                : LaurelFactory.laurelLocalName(ctx.method.renames, name);
        return new AstNode<>(new Variable.Local(LaurelFactory.reservedIdent(laurelName)), SourceMap.UNKNOWN_RANGE);
    }

    /** STORE: declaration-or-assignment decision ladder. */
    void store(StoreInstruction si) {
        String desc = localDesc(si.slot());
        AstNode<StmtExpr> val = coerceTo(stack.pop(), desc);
        if (desc != null) {
            // A local with no recorded type keeps the value's own (untyped declaration).
            arrays.refuseAsObject(val, desc);
        }
        // Capturing the ThrowsOnCase handle in a local: the declaration ends
        // the contract block: the declaration's TYPE registers its composite
        // sort (a side effect the emitted program keeps), then the throwsOn
        // initializer refuses.
        SpecStmt spec = specStmts.get(val);
        if (spec != null && spec.name().equals("throwsOn")) {
            if (!declared.contains(varKey(si.slot()))) {
                TypeRef t = localTypeRef(si.slot());
                if (t != null) {
                    t.toLaurel(ctx.typeParams, ctx.methodTypeParams, ctx.resolver, ctx.composites);
                }
            }
            throw new JavaViolationException("Unsupported JVerify method: throwsOn");
        }
        if (desc == null && !loadedSlots.contains(si.slot())
                && (locals == null || locals.at(si.slot(), pos) == null)) {
            freshName(si.slot());
        }
        // javac reuses a slot with no LVT entry for values of DIFFERENT types:
        // a change of type starts a new minted name.
        if (desc == null) {
            String vd = descOf.get(val);
            if (vd != null && !vd.equals(slotType.get(si.slot()))) {
                freshName(si.slot());
                slotType.put(si.slot(), vd);
            }
        }
        if (asIncrementExpr(si.slot(), val)) {
            return;
        }
        if (declared.add(varKey(si.slot()))) {
            // First store to the slot: a local declaration.
            String nm = localName(si.slot());
            LaurelFactory.refuseReservedName(nm);
            TypeRef t = localTypeRef(si.slot());
            ParameterOpt param = t == null
                    ? new ParameterOpt(LaurelFactory.ident(nm), Optional.empty())
                    : new ParameterOpt(LaurelFactory.ident(nm),
                            Optional.of(LaurelFactory.node(
                                    t.toLaurel(ctx.typeParams, ctx.methodTypeParams,
                                            ctx.resolver, ctx.composites))));
            var declare = new AstNode<Variable>(new Variable.Declare(param), SourceMap.UNKNOWN_RANGE);
            addStmt(new StmtExpr.Assign(List.of(declare), val));
        } else {
            addStmt(new StmtExpr.Assign(List.of(localVarTarget(si.slot())), val));
        }
    }

    /** IINC: postfix absorb, prefix defer, else a compound-assignment statement. */
    void increment(IncrementInstruction ii) {
        String nm = localName(ii.slot());
        int c = ii.constant();
        if ((c == 1 || c == -1) && replaceStackedRead(nm, c > 0)) {
            return;
        }
        if ((c == 1 || c == -1) && !stack.isEmpty()) {
            pendingIncSlot = ii.slot();
            pendingIncDelta = c;
            return;
        }
        addStmt(new StmtExpr.CompoundAssign(c >= 0 ? new Operation.Add() : new Operation.Sub(),
                localVarTarget(ii.slot()), e(new StmtExpr.LiteralInt(Math.abs(c)))));
    }

    /** Emit a deferred mid-expression iinc as the statement it would have been. */
    void drainPendingInc() {
        if (pendingIncSlot < 0) {
            return;
        }
        int slot = pendingIncSlot;
        int c = pendingIncDelta;
        pendingIncSlot = -1;
        addStmt(new StmtExpr.CompoundAssign(c >= 0 ? new Operation.Add() : new Operation.Sub(),
                localVarTarget(slot), e(new StmtExpr.LiteralInt(Math.abs(c)))));
    }

    /** Replace an unconsumed stacked read of {@code nm} with a postfix inc/dec. */
    boolean replaceStackedRead(String nm, boolean inc) {
        if (stack.isEmpty()) {
            return false;
        }
        AstNode<StmtExpr> top = stack.peek();
        String laurelName = nm.equals("this")
                ? LaurelFactory.LAUREL_SELF_BINDING : LaurelFactory.laurelLocalName(ctx.method.renames, nm);
        if (!(top.val() instanceof StmtExpr.Var var)
                || !(var.var_() instanceof Variable.Local local)
                || !local.name().text().equals(laurelName)) {
            return false;
        }
        AstNode<StmtExpr> old = stack.pop();
        AstNode<StmtExpr> n = push(new StmtExpr.IncrDecr(new IncrDecrMode.Post(),
                inc ? (IncrDecrOp) new IncrDecrOp.Incr() : new IncrDecrOp.Decr(),
                new AstNode<>(new Variable.Local(LaurelFactory.ident(laurelName)), SourceMap.UNKNOWN_RANGE)));
        String d = descOf.get(old);
        if (d != null) {
            noteDesc(n, d);
        }
        return true;
    }

    /**
     * Recover {@code x++} / {@code ++x} from a store whose value updates the
     * same slot by one while a value is still live on the stack (the local
     * case of {@link #asIncrementExprOn}).
     */
    boolean asIncrementExpr(int slot, AstNode<StmtExpr> val) {
        return asIncrementExprOn(localVarTarget(slot), val);
    }

    boolean asIncrementExprOn(AstNode<Variable> target, AstNode<StmtExpr> val) {
        int step = selfUpdateStep(target, val);
        if (step == 0 || stack.isEmpty()) {
            return false;
        }
        AstNode<StmtExpr> targetAsExpr = e(new StmtExpr.Var(target.val()));
        boolean post = LaurelTrees.structuralEquals(stack.peek(), targetAsExpr);
        if (!post && !LaurelTrees.structuralEquals(stack.peek(), val)) {
            return false;
        }
        AstNode<StmtExpr> old = stack.pop();
        AstNode<StmtExpr> n = push(new StmtExpr.IncrDecr(
                post ? new IncrDecrMode.Post() : new IncrDecrMode.Pre(),
                step > 0 ? (IncrDecrOp) new IncrDecrOp.Incr() : new IncrDecrOp.Decr(),
                target));
        String d = descOf.get(old);
        if (d != null) {
            noteDesc(n, d);
        }
        return true;
    }

    /** +1/-1 if val is target ± 1 (allowing a byte/short/char narrowing cast). */
    private int selfUpdateStep(AstNode<Variable> target, AstNode<StmtExpr> val) {
        StmtExpr v = val.val();
        // A narrowing conversion wraps the update in a cast, modeled as a
        // $cast_to_* helper call at conversion time, so the raw update
        // shape here is the $add/$sub StaticCall itself.
        if (!(v instanceof StmtExpr.StaticCall call) || call.arguments().size() != 2) {
            return 0;
        }
        String proc = call.callee().text();
        boolean plus = proc.equals("$add");
        if (!plus && !proc.equals("$sub")) {
            return 0;
        }
        AstNode<StmtExpr> targetAsExpr = e(new StmtExpr.Var(target.val()));
        if (!LaurelTrees.structuralEquals(call.arguments().get(0), targetAsExpr)) {
            return 0;
        }
        return call.arguments().get(1).val() instanceof StmtExpr.LiteralInt one && one.value() == 1
                ? (plus ? 1 : -1) : 0;
    }

    // --- Operators (arithmetic, comparison and boolean ops) ---

    /** Binary $op push. */
    void bin(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        push(LaurelFactory.primitiveOp(op, a, b));
    }

    /**
     * Integer (int/long) division or remainder: throws ArithmeticException on a
     * zero divisor. Recorded as an implicit throw on the operation's node; see
     * {@link #explicitImplicitThrows}.
     */
    void intDivRem(Operation op) {
        AstNode<StmtExpr> divisor = stack.peek();
        bin(op);
        implicitThrows.put(stack.peek(), new ImplicitThrow(
                e(LaurelFactory.primitiveOp(new Operation.Eq(), divisor, e(new StmtExpr.LiteralInt(0)))),
                ARITHMETIC_EXCEPTION));
    }

    // --- Implicit runtime exceptions ---

    static final String ARITHMETIC_EXCEPTION = "java.lang.ArithmeticException";
    static final String CLASS_CAST_EXCEPTION = "java.lang.ClassCastException";

    /**
     * A runtime exception an expression node throws implicitly when
     * {@code condition} holds: ArithmeticException for an integer division by
     * zero, ClassCastException for a failing reference cast.
     */
    record ImplicitThrow(AstNode<StmtExpr> condition, String exceptionFqn) {
    }

    /** The implicit throw of each operation node that has one (identity-keyed). */
    final Map<AstNode<StmtExpr>, ImplicitThrow> implicitThrows = new IdentityHashMap<>();

    /**
     * The explicit {@code if (condition) { throw new E }} statements to place
     * immediately before statement {@code root}, one per implicit throw that
     * Java evaluates before anything else can have an effect, in evaluation
     * order: first those of the operands still on the stack -- Java evaluated
     * them before {@code root} even if a later statement consumes them
     * ({@code a / b + (x = 5)} emits {@code x = 5} first) -- then those of
     * {@code root}. Each implicit throw is decided once, here. Laurel then
     * treats each as a real throw: an error only if it is reachable and not
     * caught.
     *
     * <p>An implicit throw stays implicit -- its operation keeps the Laurel
     * precondition that asserts it does not happen ({@code $divT}'s non-zero
     * divisor, {@code as}'s type test) -- wherever making it explicit would
     * change the meaning or the method's shape:
     * <ul>
     *   <li>in a contract, a spec lambda, a {@code check}/{@code assume}, or a
     *       {@code @Transparent} method (a transparent function cannot throw);</li>
     *   <li>under a conditional sub-expression (a ternary arm, the right
     *       operand of {@code &&}/{@code ||}), which may not be evaluated, and
     *       in any conditional or repeated context ({@link #inConditional}: a
     *       value arm, a loop header, a boolean region past its first test),
     *       where a statement would land in an expression block or run at the
     *       wrong time;</li>
     *   <li>after a call or allocation in the same statement, whose effect
     *       Java performs before the throw;</li>
     *   <li>when the exception would leave a method whose {@code throws}
     *       clause does not cover it, and no enclosing {@code catch} does
     *       (Laurel rejects that escape statically).</li>
     * </ul>
     * Once one implicit throw stays implicit, every later one in the statement
     * does too, so the explicit throws never run ahead of an earlier check.
     */
    List<AstNode<StmtExpr>> explicitImplicitThrows(AstNode<StmtExpr> root) {
        if (ctx.method.inContract || ctx.method.pure || inConditional()
                || root.val() instanceof StmtExpr.Assert || root.val() instanceof StmtExpr.Assume) {
            return List.of();
        }
        var found = new ArrayList<ImplicitThrow>();
        boolean[] blocked = {false};
        for (AstNode<StmtExpr> pending : stack.reversed()) {
            collectImplicitThrows(pending, found, blocked);
        }
        collectImplicitThrows(root, found, blocked);
        var out = new ArrayList<AstNode<StmtExpr>>();
        for (ImplicitThrow t : found) {
            ctx.composites.registerHierarchyUpToThrowable(t.exceptionFqn());
            AstNode<StmtExpr> thrown = e(new StmtExpr.Throw(
                    e(new StmtExpr.New(LaurelFactory.ident(t.exceptionFqn()), List.of()))));
            out.add(e(new StmtExpr.IfThenElse(t.condition(),
                    e(new StmtExpr.Block(List.of(thrown), Optional.empty(), List.of(), Optional.empty())), Optional.empty())));
        }
        return out;
    }

    /** Post-order (evaluation order) walk of the unconditionally evaluated nodes. */
    private void collectImplicitThrows(AstNode<StmtExpr> n, List<ImplicitThrow> out, boolean[] blocked) {
        for (AstNode<StmtExpr> c : unconditionalChildren(n.val())) {
            collectImplicitThrows(c, out, blocked);
        }
        ImplicitThrow t = implicitThrows.remove(n);
        if (t != null) {
            if (!blocked[0] && mayThrowHere(t.exceptionFqn())) {
                out.add(t);
            } else {
                blocked[0] = true;
            }
        }
        if (!isEffectFree(n.val())) {
            blocked[0] = true;
        }
    }

    /** The children Java always evaluates, in evaluation order. */
    private static List<AstNode<StmtExpr>> unconditionalChildren(StmtExpr v) {
        return switch (v) {
            case StmtExpr.StaticCall sc -> isShortCircuit(sc) && !sc.arguments().isEmpty()
                    ? List.of(sc.arguments().get(0)) : sc.arguments();
            case StmtExpr.InstanceCall ic -> {
                var l = new ArrayList<AstNode<StmtExpr>>();
                l.add(ic.target());
                l.addAll(ic.arguments());
                yield l;
            }
            case StmtExpr.Var var when var.var_() instanceof Variable.Field f -> List.of(f.target());
            case StmtExpr.ReferenceEquals r -> List.of(r.lhs(), r.rhs());
            case StmtExpr.AsType a -> List.of(a.target());
            case StmtExpr.IsType i -> List.of(i.target());
            case StmtExpr.IfThenElse i -> List.of(i.cond());
            case StmtExpr.Assign a -> {
                var l = new ArrayList<AstNode<StmtExpr>>();
                for (var t : a.targets()) {
                    if (t.val() instanceof Variable.Field f) {
                        l.add(f.target());
                    }
                }
                l.add(a.value());
                yield l;
            }
            case StmtExpr.Return r -> r.values();
            case StmtExpr.Throw t -> List.of(t.value());
            default -> List.of();
        };
    }

    private static boolean isShortCircuit(StmtExpr.StaticCall sc) {
        String name = sc.callee().text();
        return name.equals(LaurelFactory.operationProcName(new Operation.AndThen()))
                || name.equals(LaurelFactory.operationProcName(new Operation.OrElse()))
                || name.equals(LaurelFactory.operationProcName(new Operation.Implies()));
    }

    /**
     * Evaluating the node itself (not its children) has no effect. Static calls
     * to Strata's {@code $} operators and the frontend's {@code java?} helpers are
     * pure; any other call, an allocation, or a statement form is not.
     */
    private static boolean isEffectFree(StmtExpr v) {
        return switch (v) {
            case StmtExpr.StaticCall sc -> sc.callee().text().startsWith("$")
                    || sc.callee().text().startsWith("java?");
            case StmtExpr.LiteralInt _, StmtExpr.LiteralBool _, StmtExpr.LiteralString _,
                 StmtExpr.LiteralDecimal _, StmtExpr.LiteralBv _, StmtExpr.Var _, StmtExpr.This _,
                 StmtExpr.ReferenceEquals _, StmtExpr.AsType _, StmtExpr.IsType _,
                 StmtExpr.IfThenElse _, StmtExpr.Quantifier _, StmtExpr.Old _, StmtExpr.Fresh _ -> true;
            default -> false;
        };
    }

    /**
     * Laurel lets an exception leave this method only if it declares no
     * {@code throws} (the escape is then asserted not to happen), a covering
     * one, or an inferred one (widened to cover it); otherwise an enclosing
     * {@code catch} must cover it.
     *
     * <p>An enclosing {@code catch} arm unrelated to the exception (an
     * {@code IllegalStateException} arm around a division) is no obstacle: Laurel
     * accepts a guard testing the caught exception against an unrelated type
     * and lowers that test to {@code false}, and the handler reads its binding
     * at the arm's type (see {@link #catchCasts}), so the wider join
     * Laurel then types the binding at does not change what the handler sees.
     */
    private boolean mayThrowHere(String exceptionFqn) {
        if (ctx.method.enclosingCatchTypes.stream().anyMatch(arms ->
                arms.stream().anyMatch(c -> ctx.resolver.isSubclassOf(exceptionFqn, c)))) {
            return true;
        }
        String join = ctx.method.throwsJoin;
        return join == null || ctx.method.throwsInferred || ctx.resolver.isSubclassOf(exceptionFqn, join);
    }

    /**
     * A bitwise/shift operator. Reported as a hard error AT THE OPERATOR, rather
     * than deferred into a whole-method refusal: the bit-level opcodes survive into bytecode intact,
     * so there is nothing source-only about this check. Translation continues
     * (with a placeholder) so EVERY occurrence is reported, and
     * {@code constructErrorReported} turns the method into a demotion at the end.
     *
     * @param symbol  the Java operator as the diagnostic prints it ({@code &}, {@code <<})
     * @param isLong  the operand type is {@code long} rather than {@code int}
     */
    void binRefused(String symbol, boolean isLong) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        // `~x` compiles to `x ^ -1`, so an IXOR against the literal -1 is
        // reported as the unary form the user wrote. An explicit `x ^ -1` is
        // indistinguishable in bytecode and gets the same message; both are
        // unsupported, so only the naming differs.
        String type = isLong ? "long" : (isBool(a) || isBool(b)) ? "boolean" : "int";
        String rendered = symbol.equals("^") && isMinusOneLit(b.val())
                ? "~(" + type + ")"
                : symbol + "(" + type + "," + type + ")";
        push(LaurelFactory.primitiveOp(new Operation.Add(), a, b));
        reportConstructError("operator " + rendered + " is not supported");
    }

    /**
     * The constant {@code -1}, in either form it reaches here: a negative literal
     * is emitted as {@code $neg(1)} (LaurelFactory.longLiteral), not as a negative
     * {@code LiteralInt}.
     */
    private static boolean isMinusOneLit(StmtExpr e) {
        if (e instanceof StmtExpr.LiteralInt i) {
            return i.value() == -1;
        }
        return e instanceof StmtExpr.StaticCall c
                && "$neg".equals(c.callee().text())
                && c.arguments().size() == 1
                && c.arguments().get(0).val() instanceof StmtExpr.LiteralInt one
                && one.value() == 1;
    }

    /**
     * Report a hard error at the construct currently being translated and mark
     * the method so its refusal warning is suppressed (see
     * {@code MethodInfo.constructErrorReported}). Translation continues, so a
     * body with several offending constructs reports each one.
     */
    void reportConstructError(String message) {
        reportConstructError(ctx.src.positionRange(stmtRange(pos, false)), message);
    }

    /** {@link #reportConstructError(String)}, anchored at the current instruction's line ({@link #comparisonRange}). */
    void reportConstructErrorOnLine(String message) {
        reportConstructError(ctx.src.pointOf(comparisonRange()), message);
    }

    /** {@link #reportConstructError(String)}, anchored at {@code range}. */
    private void reportConstructError(org.strata.jverify.common.Range range, String message) {
        if (ctx.contractMode) {
            throw new JavaViolationException(message);
        }
        // An opted-out method still DEMOTES -- the construct is untranslatable
        // either way -- but reports nothing, which is what @Verify(false) asks for
        // (ClassLevelOptOutSuppressesConstructs). The opt-out is the resolved one,
        // so a class-level @Verify(false) covers its methods.
        if (!ctx.method.info.quiet) {
            ctx.reporter.reportError(ctx.src.uri, range, message);
        }
        ctx.method.info.constructErrorReported = true;
    }

    void neg() {
        AstNode<StmtExpr> v = stack.pop();
        // Literal folding: a negated literal folds into the literal itself.
        if (v.val() instanceof StmtExpr.LiteralInt li) {
            push(pinnedLongLiteral(-li.value()));
            return;
        }
        push(LaurelFactory.primitiveOp(new Operation.Neg(), v));
    }

    /** LCMP/FCMPL/FCMPG/DCMPL/DCMPG: register the 3-way pair; defer the unconsumed refusal. */
    void push3Way(String boxType, Boolean nanHigh) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        @SuppressWarnings("unchecked")
        AstNode<StmtExpr>[] pair = new AstNode[]{a, b};
        AstNode<StmtExpr> n = push(new StmtExpr.StaticCall(
                LaurelFactory.ident("java?cmp3$" + boxType), List.of(a, b), List.of()));
        cmp3.put(n, pair);
        if (nanHigh != null) {
            cmp3NanHigh.put(n, nanHigh);
        }
        // Unconsumed, a bare 3-way compare surfaces as a Long.compare-style
        // call to an unknown class and refuses.
        defer(n, "Call to unknown class '" + boxType + "' is not supported");
    }

    // --- Boolean materialization (comparisons and unary boolean ops) ---

    /** The materialized boolean is the NEGATION of the branch condition. */
    AstNode<StmtExpr> materializeComparison(java.lang.classfile.Opcode op) {
        return switch (op) {
            case IF_ICMPEQ -> cmp(new Operation.Neq());
            case IF_ICMPNE -> cmp(new Operation.Eq());
            // The materialized boolean is the NEGATION of the branch condition, so
            // the operator the user WROTE is the opposite of the opcode: Java
            // `a == b` compiles to `if_acmpne <else>`.
            case IF_ACMPEQ -> cmpRef(new Operation.Neq(), "!=");
            case IF_ACMPNE -> cmpRef(new Operation.Eq(), "==");
            case IF_ICMPLT -> cmp(new Operation.Geq());
            case IF_ICMPGE -> cmp(new Operation.Lt());
            case IF_ICMPGT -> cmp(new Operation.Leq());
            case IF_ICMPLE -> cmp(new Operation.Gt());
            case IFNULL -> cmpNull(new Operation.Neq());
            case IFNONNULL -> cmpNull(new Operation.Eq());
            case IFLT -> cmpZero(new Operation.Geq());
            case IFGE -> cmpZero(new Operation.Lt());
            case IFGT -> cmpZero(new Operation.Leq());
            case IFLE -> cmpZero(new Operation.Gt());
            case IFEQ -> unaryBool(true);
            case IFNE -> unaryBool(false);
            default -> {
                AstNode<StmtExpr> n = e(new StmtExpr.LiteralString("/*?" + op + "*/"));
                defer(n, "Unsupported expression: NameExpr");
                yield n;
            }
        };
    }

    /** Reference {@code ==} / {@code !=}: see {@link #refuseUnsoundReferenceEquality}. */
    private AstNode<StmtExpr> cmpRef(Operation op, String symbol) {
        refuseUnsoundReferenceEquality(symbol);
        return cmp(op);
    }

    private AstNode<StmtExpr> cmp(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        if ((op instanceof Operation.Eq || op instanceof Operation.Neq) && (isBool(a) || isBool(b))) {
            a = coerce(a, "Z");
            b = coerce(b, "Z");
        }
        return e(LaurelFactory.primitiveOp(op, a, b));
    }

    /**
     * The reference types whose Java identity is lost in translation -- the
     * boxed primitives (allocated afresh by {@link BoxModel}, where Java reuses
     * cached boxes), {@code String} (a Laurel string) and {@code Record} --
     * together with their supertypes, whose values may be one of them.
     */
    private static final Set<String> VALUE_TRANSLATED_TYPES = Set.of(
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Character",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.lang.String", "java.lang.Record",
            "java.lang.Object", "java.lang.Number", "java.lang.Comparable", "java.lang.CharSequence",
            "java.io.Serializable", "java.lang.constant.Constable", "java.lang.constant.ConstantDesc");

    /**
     * Refuse a reference comparison ({@code if_acmpeq}/{@code if_acmpne}) whose
     * meaning the translation would not keep.
     *
     * <p>Java's {@code ==} on references compares identity, but a value of a
     * {@linkplain #VALUE_TRANSLATED_TYPES value-translated type} or a record
     * becomes a Laurel value, where two distinct but equal objects compare
     * equal: accepting the comparison would prove what Java does not guarantee.
     * It is sound when either operand {@linkplain #keepsReferenceIdentity keeps
     * its identity}. Otherwise it is a construct error on the comparison's line
     * ({@link #reportConstructError}): every occurrence is reported and the
     * method is demoted. In a method opted out of verification the method is
     * demoted silently: its contract is not emitted, and calls to it are
     * refused at the call site. Library contract members are not checked.
     *
     * <p>A generic call's result carries the ERASED descriptor ({@code Object})
     * until a checkcast recovers its type, so it is not accused
     * (ErasedResultEquality), unless the result may be a box
     * ({@link #erasedBoxResults}). The cost: {@code id("a") == id("b")}, with
     * no cast in between, is accepted, and so is a box that reaches a generic
     * result with no wrapper visible in any type: {@code id(o)} for an
     * {@code Object o} holding a box, or {@code t.get()} for a receiver typed
     * by a type variable instantiated with a wrapper class.
     */
    private void refuseUnsoundReferenceEquality(String op) {
        if (ctx.contractMode || stack.size() < 2) {
            return;
        }
        var it = stack.iterator();
        AstNode<StmtExpr> b = it.next();
        AstNode<StmtExpr> a = it.next();
        if (keepsReferenceIdentity(a) || keepsReferenceIdentity(b)) {
            return;
        }
        if (mayHoldModelledBox(a) || mayHoldModelledBox(b)) {
            // Java caches the boxes of small values, so whether two boxes are one
            // object depends on the value: BoxModel allocates every box.
            reportConstructError(ctx.src.pointOf(comparisonRange()),
                    "'" + op + "' on boxed values is not yet supported: their identity depends on the box cache");
            return;
        }
        if (erasedResults.contains(a.val()) || erasedResults.contains(b.val())) {
            return;
        }
        reportConstructError(ctx.src.pointOf(comparisonRange()),
                "'" + op + "' is only allowed when at least one operand's type is impure");
    }

    /**
     * Whether {@code n} may be a box {@link BoxModel} translates: its static
     * type is a modelled wrapper class, or it is a generic call's erased result
     * that may be one ({@link #erasedBoxResults}).
     */
    private boolean mayHoldModelledBox(AstNode<StmtExpr> n) {
        return isModelledBox(n) || erasedBoxResults.contains(n.val());
    }

    /** Whether {@code n}'s static type is a wrapper class {@link BoxModel} translates. */
    boolean isModelledBox(AstNode<StmtExpr> n) {
        String desc = descOf.get(n);
        return desc != null && desc.startsWith("L") && desc.endsWith(";")
                && ctx.composites.boxes.models(BoxModel.wrapperOf(desc.substring(1, desc.length() - 1)));
    }

    /**
     * Whether {@code n} certainly holds a heap object (or null): the
     * {@code null} constant, a value typed {@code @Impure}, or a value whose
     * static (erased) type is neither a {@linkplain #VALUE_TRANSLATED_TYPES
     * value-translated type or supertype of one} nor a record. A type
     * variable erases to its bound, so it is decided by the bound, as Java
     * knows nothing else about its instantiation. An operand of unknown type
     * may hold anything.
     */
    private boolean keepsReferenceIdentity(AstNode<StmtExpr> n) {
        if (isNullMarker(n) || impureValues.contains(n)) {
            return true;
        }
        String desc = descOf.get(n);
        TypeRef type = desc != null && (desc.startsWith("L") || desc.startsWith("["))
                ? TypeRef.ofDescriptor(desc)
                : staticTypeOf.get(n);
        if (type == null) {
            return false;
        }
        if (type.kind() == TypeRef.Kind.ARRAY) {
            return true;
        }
        String fqn = type.fqnFromContext();
        return fqn != null
                && !VALUE_TRANSLATED_TYPES.contains(fqn)
                && !"java.lang.Record".equals(ctx.resolver.superclassOf(fqn));
    }

    /**
     * The current instruction's position (a comparison's, an unboxing's): its
     * LineNumberTable line, else the enclosing statement's span.
     */
    private FileRange comparisonRange() {
        if (lines != null && seqToBci != null && pos >= 0 && pos < seqToBci.length) {
            FileRange r = ctx.src.lineRange(lines.lineAt(seqToBci[pos]));
            if (r != SourceMap.UNKNOWN_RANGE) {
                return r;
            }
        }
        return stmtRange(pos, false);
    }

    private AstNode<StmtExpr> cmpNull(Operation op) {
        AstNode<StmtExpr> a = stack.pop();
        AstNode<StmtExpr> nul = e(nullMarker());
        defer(nul, "Unsupported expression: NullLiteralExpr");
        return e(LaurelFactory.primitiveOp(op, a, nul));
    }

    private AstNode<StmtExpr> cmpZero(Operation op) {
        AstNode<StmtExpr> a = stack.pop();
        AstNode<StmtExpr>[] pair = cmp3.get(a);
        if (pair != null) {
            return relational(a, pair[0], op, pair[1]);
        }
        return e(LaurelFactory.primitiveOp(op, a, e(new StmtExpr.LiteralInt(0))));
    }

    private AstNode<StmtExpr> unaryBool(boolean trueWhenNonZero) {
        AstNode<StmtExpr> a = stack.pop();
        Operation op = trueWhenNonZero ? new Operation.Neq() : new Operation.Eq();
        AstNode<StmtExpr>[] pair = cmp3.get(a);
        if (pair != null) {
            return relational(a, pair[0], op, pair[1]);
        }
        if (isBool(a)) {
            return trueWhenNonZero ? a : LaurelTrees.negate(a, ctx.method.unordered);
        }
        return e(LaurelFactory.primitiveOp(op, a, e(new StmtExpr.LiteralInt(0))));
    }

    /**
     * Rebuild a relational test from a 3-way comparison, keeping NaN on the
     * correct side. NaN-unordered comparisons are recorded in the
     * context's identity set (their relational negation must not flip).
     */
    private AstNode<StmtExpr> relational(AstNode<StmtExpr> src, AstNode<StmtExpr> a,
                                         Operation op, AstNode<StmtExpr> b) {
        Boolean nanHigh = cmp3NanHigh.get(src);
        if (nanHigh != null) {
            boolean high = op instanceof Operation.Geq || op instanceof Operation.Gt;
            if (high == nanHigh) {                      // the fall path includes NaN
                Operation strict = switch (op) {
                    case Operation.Geq g -> new Operation.Lt();
                    case Operation.Gt g -> new Operation.Leq();
                    case Operation.Lt l -> new Operation.Geq();
                    case Operation.Leq l -> new Operation.Gt();
                    default -> null;
                };
                if (strict != null) {
                    StmtExpr inner = LaurelFactory.primitiveOp(strict, a, b);
                    ctx.method.unordered.add(inner);
                    return e(LaurelFactory.primitiveOp(new Operation.Not(), e(inner)));
                }
            }
        }
        StmtExpr rel = LaurelFactory.primitiveOp(op, a, b);
        if (nanHigh != null) {
            ctx.method.unordered.add(rel);
        }
        return e(rel);
    }

    // ================= Part 2: dispatch, stack ops, arrays, calls =================

    /** Real calls / allocations (POP of one becomes an expression statement). */
    final Set<AstNode<StmtExpr>> callNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * User-call nodes -> the {@code declaringFqn_name} key of the driver's
     * pureMethods map: the heap-write fallback ({@code writesHeap})
     * walks the emitted Laurel tree and answers call purity through this.
     */
    final Map<StmtExpr, String> calleeKeys = new IdentityHashMap<>();
    /** A user call's resolved callee: its declaring class and classfile method. */
    record CalleeRef(String declaringFqn, java.lang.classfile.MethodModel method) {
    }
    /** User call nodes -> their callee, for {@code preconditionOf} (see SpecCallTranslator). */
    final Map<StmtExpr, CalleeRef> calleeModels = new IdentityHashMap<>();
    /**
     * Boxes -> the primitive value they box. A box of a class {@link BoxModel}
     * translates is a call of its boxing helper, and a consumer that only needs
     * the primitive takes it from here (InvokeTranslator.unboxed). Any other
     * box is a {@code $box} marker: javac wraps an int-returning call in
     * {@code Integer.valueOf} before passing it to {@code preconditionOf(Object)},
     * where the box is the identity, so the marker is unwrapped. Anywhere else
     * the marker refuses as a call to an unknown class.
     */
    final Map<AstNode<StmtExpr>, AstNode<StmtExpr>> boxedValue = new IdentityHashMap<>();
    /**
     * {@code INSTANCEOF} instructions that test a pattern-bind shape, filled by
     * {@link ControlFlowStructurer#markPatternBindTests}. Laurel's {@code is} has
     * nowhere to put the binding, so an {@code instanceof} with a pattern
     * refuses.
     */
    final java.util.Set<java.lang.classfile.instruction.TypeCheckInstruction> patternBindTests =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Call results whose DECLARED type is a type variable, so the
     * {@code CHECKCAST} javac puts after them is an erasure artefact rather than
     * a cast the user wrote. In the source, {@code identity(x)} already has the
     * instantiated type and the cast is a no-op. From bytecode only the erased
     * {@code Object -> C} remains, which is a genuine downcast on its face, so the
     * provenance has to be carried here.
     */
    final java.util.Set<StmtExpr> erasedResults =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The {@link #erasedResults} that may be a modelled box, judged by the
     * call's receiver and arguments (InvokeTranslator.passesModelledBox): one
     * is a box ({@code id(1)} for {@code <T> T id(T x)}), or its static type
     * names a wrapper class at any depth ({@code p.get()} for
     * {@code Box<Integer> p}), is raw, or is unknown. Reference comparison
     * refuses them as boxes.
     */
    final java.util.Set<StmtExpr> erasedBoxResults =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Spec-call statements (precondition/postcondition/.../throwsOn chains)
     * emitted as marker statements: the driver's contract extraction reads
     * this table. A marker in body position past the header refuses with
     * "call to contract method must come before the rest of the body".
     */
    final Map<AstNode<StmtExpr>, SpecStmt> specStmts = new IdentityHashMap<>();

    /** One recognized spec-call statement (or a throwsOn(...).x(...).y(...) chain). */
    record SpecStmt(String name, List<AstNode<StmtExpr>> args, BodyTranslator.LambdaHandle lambdaArg,
                    List<SpecLink> links, int seqIdx) {
    }

    /** One chained clause of a throwsOn case. */
    record SpecLink(String name, AstNode<StmtExpr> arg, BodyTranslator.LambdaHandle lambdaArg) {
    }

    /** Emitted Throw statements -> the thrown static type fqn (or null). */
    final Map<AstNode<StmtExpr>, String> throwStmts = new IdentityHashMap<>();

    /** Contract mode: the value a {@code new ContractException()} pushes. */
    static final String CONTRACT_EXCEPTION_MARKER = "$contractException";
    /**
     * Contract mode: the placed statements standing for a
     * {@code throw new ContractException()}; member translation accepts one
     * only as the whole body.
     */
    final Set<AstNode<StmtExpr>> contractThrows = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Structuring gaps recorded by {@link #todo}. Method translation checks
     * these FIRST and refuses the whole method with the first gap's text
     * ("unsupported control flow ..."), ahead of every other refusal in the
     * body.
     */
    final List<String> todos = new ArrayList<>();

    void todo(String msg) {
        todos.add("TODO unsupported: " + msg);
    }

    // --- Dispatch (translateInstrs / translateSpan) ---

    static int seqAt(List<Integer> idxs, int p) {
        return idxs == null ? p : idxs.get(p);
    }

    /**
     * The reference locals of {@code seq}, as {@link #localKey}s, that some
     * {@code astore} fills with a value read from the heap, which may be null:
     * a field read ({@code getfield}/{@code getstatic}), an array element
     * ({@code aaload}), a load of a local already in the set, or a
     * {@code checkcast} of any of these. The value stored comes from the
     * instructions {@link #valueSources} finds, which include a conditional
     * expression's arms, looking through a {@code dup} and the stores of a
     * chained assignment ({@code b = c = h.f}). A local is in the set wherever
     * it is loaded, in every LocalVariableTable entry of the same name and
     * type; a slot javac reuses for a local of another name is a different key.
     */
    static Set<String> heapReadSlots(List<CodeElement> seq, Locals locals) {
        var gotosTo = new HashMap<java.lang.classfile.Label, List<Integer>>();
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i) instanceof java.lang.classfile.instruction.BranchInstruction br
                    && br.opcode() == java.lang.classfile.Opcode.GOTO) {
                gotosTo.computeIfAbsent(br.target(), k -> new ArrayList<>()).add(i);
            }
        }
        var slots = new java.util.HashSet<String>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < seq.size(); i++) {
                if (seq.get(i) instanceof StoreInstruction st
                        && st.typeKind() == java.lang.classfile.TypeKind.REFERENCE
                        && !slots.contains(localKey(locals, st.slot(), i))
                        && storesHeapRead(seq, i, gotosTo, slots, locals)) {
                    slots.add(localKey(locals, st.slot(), i));
                    changed = true;
                }
            }
        }
        return slots;
    }

    /**
     * The local in {@code slot} at seq index {@code pos}: the slot with the
     * name and descriptor of its LocalVariableTable entry, or the slot alone
     * where no entry covers it ({@link Locals#at}, which also takes the entry a
     * store opens). A local assigned in branches has one entry per branch and
     * one after the join, all with the same key; two locals of the same name
     * and type in one slot share it too, which only refuses more.
     */
    static String localKey(Locals locals, int slot, int pos) {
        Locals.Scope s = locals == null ? null : locals.at(slot, pos);
        return s == null ? Integer.toString(slot) : slot + ":" + s.name() + ":" + s.desc();
    }

    /** Whether a value the instruction at {@code at} consumes may be read from the heap ({@link #heapReadSlots}). */
    private static boolean storesHeapRead(List<CodeElement> seq, int at,
                                          Map<java.lang.classfile.Label, List<Integer>> gotosTo, Set<String> slots,
                                          Locals locals) {
        var producers = new ArrayList<Integer>();
        valueSources(seq, at, gotosTo, producers);
        for (int p : producers) {
            if (p < 0) {
                continue;
            }
            switch (seq.get(p)) {
                case java.lang.classfile.instruction.FieldInstruction fi
                        when fi.opcode() == java.lang.classfile.Opcode.GETFIELD
                                || fi.opcode() == java.lang.classfile.Opcode.GETSTATIC -> {
                    return true;
                }
                case java.lang.classfile.instruction.ArrayLoadInstruction al
                        when al.typeKind() == java.lang.classfile.TypeKind.REFERENCE -> {
                    return true;
                }
                case LoadInstruction li when slots.contains(localKey(locals, li.slot(), p)) -> {
                    return true;
                }
                case java.lang.classfile.instruction.TypeCheckInstruction tc
                        when tc.opcode() == java.lang.classfile.Opcode.CHECKCAST -> {
                    if (storesHeapRead(seq, p, gotosTo, slots, locals)) {
                        return true;
                    }
                }
                // A dup copies its operand, and in a chained assignment the store of
                // one copy sits between the dup and the value's next consumer.
                case java.lang.classfile.instruction.StackInstruction si
                        when si.opcode() == java.lang.classfile.Opcode.DUP -> {
                    if (storesHeapRead(seq, p, gotosTo, slots, locals)) {
                        return true;
                    }
                }
                case StoreInstruction st when st.typeKind() == java.lang.classfile.TypeKind.REFERENCE -> {
                    if (storesHeapRead(seq, p, gotosTo, slots, locals)) {
                        return true;
                    }
                }
                default -> { }
            }
        }
        return false;
    }

    /**
     * Add to {@code out} the instructions that may leave the value the
     * instruction at {@code at} consumes: the instruction before it, and,
     * for each label in between, each {@code goto} to that label with the
     * instructions that leave the value it carries, found the same way.
     * Labels are skipped: javac's CharacterRangeTable puts them at statement
     * and expression boundaries.
     */
    private static void valueSources(List<CodeElement> seq, int at,
                                     Map<java.lang.classfile.Label, List<Integer>> gotosTo, List<Integer> out) {
        int j = at - 1;
        while (j >= 0 && seq.get(j) instanceof java.lang.classfile.instruction.LabelTarget lt) {
            for (int g : gotosTo.getOrDefault(lt.label(), List.of())) {
                if (!out.contains(g)) {
                    out.add(g);    // a goto leaves no value: it only marks the arm as visited
                    valueSources(seq, g, gotosTo, out);
                }
            }
            j--;
        }
        if (j >= 0 && !out.contains(j)) {
            out.add(j);
        }
    }

    void run(List<CodeElement> seq) {
        int k = 0;
        while (k < seq.size()) {
            pos = k;
            CodeElement el = seq.get(k);
            if (el instanceof java.lang.classfile.instruction.LabelTarget) {
                k++;
                continue;
            }
            Instruction insn = (Instruction) el;
            if (insn instanceof java.lang.classfile.instruction.BranchInstruction br
                    && ClassfileQueries.isConditionalBranch(br.opcode())) {
                int consumed = tryBooleanRegion(seq, k, br);
                if (consumed > 0) {
                    k += consumed;
                    continue;
                }
            }
            int c;
            if ((c = tryArrayInit(seq, null, k)) > 0) {
                k += c;
                continue;
            }
            if ((c = tryCompoundAssign(seq, null, k)) > 0) {
                k += c;
                continue;
            }
            translate(insn);
            k++;
        }
    }

    void translateInstrs(List<CodeElement> ins, List<Integer> idxs) {
        translateSpan(ins, idxs, 0, ins.size());
    }

    void translateSpan(List<CodeElement> seq, List<Integer> idxs, int from, int to) {
        for (int p = from; p < to; ) {
            pos = seqAt(idxs, p);
            CodeElement el = seq.get(p);
            if (el instanceof java.lang.classfile.instruction.BranchInstruction br
                    && br.opcode() == java.lang.classfile.Opcode.GOTO) {
                p++;
                continue;   // structural
            }
            int c;
            if ((c = tryArrayInit(seq, idxs, p)) > 0) {
                p += c;
                continue;
            }
            if ((c = tryCompoundAssign(seq, idxs, p)) > 0) {
                p += c;
                continue;
            }
            translate((Instruction) el);
            p++;
        }
    }

    /** One instruction to its Laurel form; then marks the value of an {@code @Impure} cast it produces. */
    void translate(Instruction in) {
        int at = pos;
        translateInstruction(in);
        if (!impureCasts.isEmpty() && producesImpureCast(in, at) && !stack.isEmpty()) {
            impureValues.add(stack.peek());
        }
    }

    /**
     * Whether {@code in}, at seq index {@code at}, leaves an {@code @Impure}
     * cast's value on the stack (see {@link #impureCasts}): a {@code checkcast}
     * the CAST entry names, or the instruction just before the bci a cast that
     * emits no instruction names.
     */
    private boolean producesImpureCast(Instruction in, int at) {
        if (seqToBci == null || at < 0 || at >= seqToBci.length) {
            return false;
        }
        int bci = seqToBci[at];
        if (in instanceof java.lang.classfile.instruction.TypeCheckInstruction tc
                && tc.opcode() == java.lang.classfile.Opcode.CHECKCAST && impureCasts.contains(bci)) {
            return true;
        }
        return impureCasts.contains(bci + in.sizeInBytes());
    }

    /** MAIN opcode switch: one instruction to its Laurel form. */
    private void translateInstruction(Instruction in) {
        if (!rangeCastAnnotations.isEmpty() && seqToBci != null && pos >= 0 && pos < seqToBci.length
                && !(in instanceof java.lang.classfile.instruction.ConvertInstruction)
                && !(in instanceof java.lang.classfile.instruction.TypeCheckInstruction)) {
            var annos = rangeCastAnnotations.get(seqToBci[pos]);
            if (annos != null) {
                applyRangeCast(annos);
            }
        }
        if (pendingIncSlot >= 0
                && !(in instanceof LoadInstruction li && li.slot() == pendingIncSlot)) {
            drainPendingInc();
        }
        switch (in) {
            case java.lang.classfile.instruction.ConstantInstruction ci -> pushConstant(ci.constantValue());
            case LoadInstruction li -> load(li);
            case StoreInstruction si -> store(si);
            case IncrementInstruction ii -> increment(ii);
            case java.lang.classfile.instruction.OperatorInstruction oi -> operator(oi);
            case java.lang.classfile.instruction.ConvertInstruction cv -> convert(cv);
            case java.lang.classfile.instruction.TypeCheckInstruction tc -> typeCheck(tc);
            case java.lang.classfile.instruction.FieldInstruction fi -> fieldAccess.field(fi);
            // The array model's arms; anything they do not handle keeps the arms below.
            case java.lang.classfile.instruction.ArrayLoadInstruction al when arrays.handlesLoad(al) ->
                    arrays.load(al);
            case java.lang.classfile.instruction.ArrayStoreInstruction as when arrays.handlesStore(as) ->
                    arrays.store(as);
            case java.lang.classfile.instruction.NewPrimitiveArrayInstruction na when arrays.handlesNewArray() ->
                    arrays.newPrimitiveArray(na);
            case java.lang.classfile.instruction.NewReferenceArrayInstruction na when arrays.enabled() ->
                    arrays.newReferenceArray(na);
            case java.lang.classfile.instruction.NewMultiArrayInstruction na when arrays.enabled() ->
                    arrays.newMultiArray();
            case java.lang.classfile.instruction.ArrayLoadInstruction al -> {
                stack.pop();
                AstNode<StmtExpr> arr = stack.pop();
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("$arrayAccess"));
                defer(n, "Unsupported expression: ArrayAccessExpr");
            }
            case java.lang.classfile.instruction.ArrayStoreInstruction as ->
                    throw new JavaViolationException("Unsupported variable expression: ArrayAccessExpr");
            case java.lang.classfile.instruction.NewPrimitiveArrayInstruction na -> {
                stack.pop();
                defer(push(new StmtExpr.LiteralString("$newArray")),
                        "Unsupported expression: ArrayCreationExpr");
            }
            case java.lang.classfile.instruction.NewReferenceArrayInstruction na -> {
                stack.pop();
                defer(push(new StmtExpr.LiteralString("$newArray")),
                        "Unsupported expression: ArrayCreationExpr");
            }
            case java.lang.classfile.instruction.NewMultiArrayInstruction na -> {
                for (int i = 0; i < na.dimensions(); i++) {
                    stack.pop();
                }
                defer(push(new StmtExpr.LiteralString("$newArray")),
                        "Unsupported expression: ArrayCreationExpr");
            }
            case java.lang.classfile.instruction.NewObjectInstruction nw -> {
                AstNode<StmtExpr> marker = push(new StmtExpr.LiteralString("$new"));
                pendingNew.put(marker, nw.className().asInternalName());
            }
            case java.lang.classfile.instruction.StackInstruction sk -> stackOp(sk, in);
            case java.lang.classfile.instruction.InvokeDynamicInstruction indy -> invokes.invokeDynamic(indy);
            case java.lang.classfile.instruction.InvokeInstruction inv -> invokes.invoke(inv);
            case java.lang.classfile.instruction.ReturnInstruction ri -> {
                if (ri.opcode() != java.lang.classfile.Opcode.RETURN) {
                    AstNode<StmtExpr> v = stack.pop();
                    if (arrays.isArrayConstructorRef(lambdas.get(v))) {
                        throw new JavaViolationException(ArrayModel.CTOR_REF);
                    }
                    arrays.refuseAsObject(v, retDesc);
                    // A returned lambda/method reference: allocate the
                    // return sort directly.
                    if (lambdas.containsKey(v) && ctx.method.returnSort != null) {
                        ctx.composites.referencedCompositeTypes.add(ctx.method.returnSort);
                        addStmt(new StmtExpr.Return(List.of(e(new StmtExpr.New(
                                LaurelFactory.ident(ctx.method.returnSort), List.of())))));
                    } else {
                        // coerceTo, not coerce: the boolean/int erasure runs BOTH
                        // ways here. `return b ? 1 : 0;` in an int-returning method
                        // compiles to the same iconst_1/iconst_0 diamond as a
                        // boolean expression, so the structurer folds it to a bool
                        // and the return needs it back as an int32.
                        addStmt(new StmtExpr.Return(List.of(coerceTo(v, retDesc))));
                    }
                }
                // A bare `return` in the middle of a method becomes the Laurel
                // short form only via the structurer; void fall-off emits nothing.
            }
            default -> {
                if (in.opcode() == java.lang.classfile.Opcode.ATHROW && ctx.contractMode
                        && stack.peek() != null
                        && stack.peek().val() instanceof StmtExpr.LiteralString marker
                        && marker.value().equals(CONTRACT_EXCEPTION_MARKER)) {
                    // `throw new ContractException()`: no coverage check, no
                    // hierarchy registration; a placeholder statement.
                    stack.pop();
                    AstNode<StmtExpr> placed = place(e(new StmtExpr.LiteralString("$contractThrow")),
                            stmtRange(pos, false));
                    stmts.add(placed);
                    contractThrows.add(placed);
                } else if (in.opcode() == java.lang.classfile.Opcode.ATHROW && ctx.method.relocatedContract) {
                    stack.pop();
                } else if (in.opcode() == java.lang.classfile.Opcode.ATHROW) {
                    AstNode<StmtExpr> v = stack.pop();
                    AstNode<StmtExpr> before = e(new StmtExpr.Throw(v));
                    checkPlaceable(before);
                    // Coverage discipline first, then the hierarchy chain
                    // link for the `is` guard.
                    String thrownFqn = thrownStaticTypeFqn(v);
                    checkThrowCoverage(thrownFqn, v);
                    ctx.composites.registerHierarchyUpToThrowable(thrownFqn);
                    FileRange range = stmtRange(pos, false);
                    for (AstNode<StmtExpr> t : explicitImplicitThrows(before)) {
                        stmts.add(place(t, range));
                    }
                    AstNode<StmtExpr> placed = place(before, range);
                    stmts.add(placed);
                    throwStmts.put(placed, thrownFqn);
                } else if (in.opcode() == java.lang.classfile.Opcode.MONITORENTER
                        || in.opcode() == java.lang.classfile.Opcode.MONITOREXIT) {
                    if (!stack.isEmpty()) {
                        stack.pop();  // structural; the synchronized wrapper is recovered separately
                    }
                } else {
                    todo(in.opcode().toString());
                }
            }
        }
    }

    void operator(java.lang.classfile.instruction.OperatorInstruction oi) {
        switch (oi.opcode()) {
            case IADD, LADD, FADD, DADD -> bin(new Operation.Add());
            case ISUB, LSUB, FSUB, DSUB -> bin(new Operation.Sub());
            case IMUL, LMUL, FMUL, DMUL -> bin(new Operation.Mul());
            case IDIV, LDIV -> intDivRem(new Operation.DivT());
            case IREM, LREM -> intDivRem(new Operation.ModT());
            case FDIV, DDIV -> bin(new Operation.DivT());
            case FREM, DREM -> bin(new Operation.ModT());
            case IAND -> binRefused("&", false);
            case LAND -> binRefused("&", true);
            case IOR -> binRefused("|", false);
            case LOR -> binRefused("|", true);
            case IXOR -> binRefused("^", false);
            case LXOR -> binRefused("^", true);
            case ISHL -> binRefused("<<", false);
            case LSHL -> binRefused("<<", true);
            case ISHR -> binRefused(">>", false);
            case LSHR -> binRefused(">>", true);
            case IUSHR -> binRefused(">>>", false);
            case LUSHR -> binRefused(">>>", true);
            case INEG, LNEG, FNEG, DNEG -> neg();
            case LCMP -> push3Way("Long", null);
            case FCMPL -> push3Way("Float", Boolean.FALSE);
            case FCMPG -> push3Way("Float", Boolean.TRUE);
            case DCMPL -> push3Way("Double", Boolean.FALSE);
            case DCMPG -> push3Way("Double", Boolean.TRUE);
            case ARRAYLENGTH -> {
                if (arrays.handlesLength()) {
                    arrays.length();
                    return;
                }
                stack.pop();
                // Without the array model, `x.length` defers a refusal
                // (wording pinned by the corpus).
                defer(push(new StmtExpr.LiteralString("$arraylength")),
                        "Unsupported expression: FieldAccessExpr");
            }
            default -> {
                todo("op_" + oi.opcode());
                push(LaurelFactory.var_("UNSUPPORTED_op_"
                        + oi.opcode().toString().replaceAll("[^A-Za-z0-9_]", "_")));
            }
        }
    }

    /** Primitive conversion via the registered cast-helper procedures. */
    void convert(java.lang.classfile.instruction.ConvertInstruction cv) {
        AstNode<StmtExpr> operand = stack.pop();
        String targetName = ClassfileQueries.convTarget(cv.opcode());   // e.g. "byte", "long"
        TypeRef target = TypeRef.ofRendered(targetName, false, false);
        HighType targetType;
        try {
            targetType = target.toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
        } catch (JavaViolationException ex) {
            // float/double conversions: the fully-qualified cast refusal.
            defer(push(new StmtExpr.LiteralString("$cast")),
                    "Cast to " + targetName + " is not yet supported");
            return;
        }
        String laurelTarget = LaurelFactory.laurelIntegerTypeName(targetType);
        if (laurelTarget == null) {
            defer(push(new StmtExpr.LiteralString("$cast")),
                    "Cast to " + targetName + " is not yet supported");
            return;
        }
        // `(short) l` compiles to `l2i; i2s`. Two range checks (int64 -> int32, then
        // int32 -> int16) hold exactly when one does (int64 -> int16), which is the
        // single obligation with a single message -- so the pair is fused.
        if (isIntNarrowing(cv.opcode()) && operand.val() instanceof StmtExpr.StaticCall prev
                && prev.callee().text().equals("java?cast_to_int32_from_int64")
                && prev.arguments().size() == 1) {
            operand = prev.arguments().get(0);
        }
        HighType sourceType = inferCastSourceType(operand);
        // Every integral WIDENING is the identity, not only one off an unbounded value:
        // it is value-preserving (JLS 5.1.2), so it carries no obligation. A cast
        // helper here would give each occurrence its own fresh result, so
        // `u * i == i * u` with `i` widened twice would compare two unrelated values
        // inside a nonlinear product -- a solver timeout instead of a proof
        // (PrimitiveCasts.unboundedTimesInt).
        if (isJavaWidening(cv.opcode())
                && (sourceType instanceof HighType.TInt || cv.opcode() == java.lang.classfile.Opcode.I2L)) {
            // The operand is the UNBOUNDED Laurel `int` (an @Unbounded-annotated
            // value). javac's widening conversion on it -- typically the i2l it
            // inserts when the value meets a long, which the source never wrote
            // -- is value-preserving in Java, so under the unbounded reading it
            // carries no obligation and cannot truncate: it is the identity.
            // Emitting a cast helper here instead would demand the unbounded
            // value fit the bounded target, which is exactly the range
            // restriction @Unbounded asks to be dropped, and which the source
            // frontend never asserts because it compares in the unbounded
            // domain. NARROWING off an unbounded value keeps its obligation.
            stack.push(operand);
            noteDesc(operand, ClassfileQueries.convDesc(cv.opcode()));
            return;
        }
        String sourceName = LaurelFactory.laurelTypeName(sourceType);
        String helperName = "java?cast_to_" + laurelTarget + "_from_" + sourceName;
        ctx.lf.registerCastHelper(helperName, sourceName, sourceType, laurelTarget, targetType);
        AstNode<StmtExpr> n = push(new StmtExpr.StaticCall(
                LaurelFactory.ident(helperName), List.of(operand), List.of()));
        noteDesc(n, ClassfileQueries.convDesc(cv.opcode()));
    }

    /** The int-to-narrower conversions javac emits after an {@code l2i} for {@code (short) l}. */
    private static boolean isIntNarrowing(java.lang.classfile.Opcode op) {
        return op == java.lang.classfile.Opcode.I2S || op == java.lang.classfile.Opcode.I2B
                || op == java.lang.classfile.Opcode.I2C;
    }

    /**
     * A cast that changes only the integer RANGE ({@code (@Unbounded @Nat int) x}
     * where {@code x} is an {@code @Unbounded int}) emits no instruction: both sides
     * are JVM {@code int}. Its type annotations survive, though, as CAST entries whose
     * offset names the instruction that consumes the cast value (javac 25). So before
     * that instruction the stack top gets the cast's obligation and message
     * ("cast from int to nat does not hold"). An unannotated `(int) x` leaves nothing
     * at all and is indistinguishable from `int i = x;`, so it stays the store's
     * subset check, which words its failure differently.
     */
    final Map<Integer, java.util.Set<String>> rangeCastAnnotations = new HashMap<>();

    /**
     * The bcis of the CAST entries that carry {@code @Impure}. A cast that
     * emits a {@code checkcast} is recorded at it; one that emits no
     * instruction ({@code (@Impure Object) b} on an {@code Object}) at the
     * instruction after the cast's operand. Either way the cast's value is
     * {@linkplain #impureValues @Impure}, so {@code a == (@Impure Object) b}
     * is a reference comparison.
     */
    final java.util.Set<Integer> impureCasts = new java.util.HashSet<>();

    private void applyRangeCast(java.util.Set<String> annos) {
        if (stack.isEmpty()) {
            return;
        }
        boolean nat = annos.contains("Nat");
        boolean unbounded = annos.contains("Unbounded");
        if (!nat && !unbounded) {
            return;
        }
        AstNode<StmtExpr> operand = stack.peek();
        String d = descOf.get(operand);
        if (d != null && !d.equals("I") && !d.equals("J") && !d.equals("S") && !d.equals("B")) {
            return;
        }
        String prim = "J".equals(d) ? "long" : "int";
        HighType targetType = TypeRef.ofRendered(prim, nat, unbounded)
                .toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
        String laurelTarget = targetType instanceof HighType.TInt ? "int"
                : LaurelFactory.laurelIntegerTypeName(targetType);
        HighType sourceType = inferCastSourceType(operand);
        String sourceName = LaurelFactory.laurelTypeName(sourceType);
        if (laurelTarget == null || laurelTarget.equals(sourceName)) {
            return;
        }
        stack.pop();
        String helperName = "java?cast_to_" + laurelTarget + "_from_" + sourceName;
        ctx.lf.registerCastHelper(helperName, sourceName, sourceType, laurelTarget, targetType);
        AstNode<StmtExpr> n = push(new StmtExpr.StaticCall(LaurelFactory.ident(helperName), List.of(operand), List.of()));
        if (d != null) {
            noteDesc(n, d);
        }
        staticTypeOf.put(n, TypeRef.ofRendered(prim, nat, unbounded));
    }

    /**
     * True for the integral widening primitive conversions (JLS 5.1.2): the
     * result holds every value of the source, so the conversion cannot lose
     * information. {@code i2l} is the one javac inserts implicitly, when an
     * {@code int} operand meets a {@code long}.
     */
    private static boolean isJavaWidening(java.lang.classfile.Opcode op) {
        return switch (op) {
            case I2L, I2F, I2D, L2F, L2D, F2D -> true;
            default -> false;
        };
    }

    /**
     * The cast operand's static type without javac attribution: a local's
     * declared type when known, a stacked
     * cast's target, int64 for long literals, else int32.
     */
    private HighType inferCastSourceType(AstNode<StmtExpr> operand) {
        TypeRef declared = staticTypeOf.get(operand);
        if (declared != null) {
            try {
                return declared.toLaurel(ctx.typeParams, ctx.methodTypeParams,
                        ctx.resolver, ctx.composites);
            } catch (JavaViolationException ignored) {
                // fall through to the defaults
            }
        }
        if (operand.val() instanceof StmtExpr.StaticCall call
                && call.callee().text().startsWith("java?cast_to_")) {
            String t = call.callee().text().substring("java?cast_to_".length());
            int from = t.indexOf("_from_");
            String name = from >= 0 ? t.substring(0, from) : t;
            return name.equals("int") ? new HighType.TInt()
                    : new HighType.UserDefined(LaurelFactory.ident(name));
        }
        String d = descOf.get(operand);
        if ("J".equals(d)) {
            return new HighType.UserDefined(LaurelFactory.ident("int64"));
        }
        return new HighType.UserDefined(LaurelFactory.ident("int32"));
    }

    /** CHECKCAST / INSTANCEOF: lowers a reference cast or type test. */
    void typeCheck(java.lang.classfile.instruction.TypeCheckInstruction tc) {
        if (arrays.enabled() && tc.type().asSymbol().isArray()) {
            throw new JavaViolationException(ArrayModel.CAST);
        }
        TypeRef t = TypeRef.ofClassDesc(tc.type().asSymbol());
        AstNode<StmtExpr> v = stack.pop();
        String fqn = resolveFqn(t);
        if (tc.opcode() == java.lang.classfile.Opcode.CHECKCAST) {
            // An erasure cast carries no obligation: the value came from a call
            // whose declared type is a type variable, so javac is restoring the
            // instantiation the source already had. Treated as the identity (see
            // erasedResults).
            if (fqn != null && ctx.resolver.libraryContracts.values().stream()
                    .anyMatch(c -> c.fqn().equals(fqn))) {
                stack.push(v);
                staticTypeOf.put(v, t);
                return;
            }
            if (erasedResults.contains(v.val())) {
                stack.push(v);
                staticTypeOf.put(v, t);
                // The cast has recovered the real type, so the value is no longer
                // "erased": record its descriptor too, or a later check reading
                // descOf would still see Object.
                noteDesc(v, tc.type().asSymbol().descriptorString());
                erasedResults.remove(v.val());
                return;
            }
            BoxModel.Wrapper box = BoxModel.wrapperOf(tc.type().asInternalName());
            if (!ctx.contractMode && ctx.composites.boxes.models(box)) {
                // A cast to a box class is a downcast to its composite (BoxModel).
                var boxType = LaurelFactory.node(new HighType.UserDefined(
                        LaurelFactory.ident(ctx.composites.boxes.declare(box))));
                AstNode<StmtExpr> n = push(new StmtExpr.AsType(v, boxType));
                staticTypeOf.put(n, t);
                noteDesc(n, box.descriptor());
                if (heapReadValues.contains(v)) {
                    heapReadValues.add(n);
                }
                implicitThrows.put(n, new ImplicitThrow(
                        e(LaurelFactory.primitiveOp(new Operation.Not(), e(new StmtExpr.IsType(v, boxType)))),
                        CLASS_CAST_EXCEPTION));
                return;
            }
            // A reference DOWNCAST is Laurel's `as`, whose embedded type test
            // becomes a real obligation. It applies to exception types and to any
            // composite THIS compilation declares: every sort carries its
            // supertype closure and a runtime tag. An
            // UPCAST never reaches here: javac emits no instruction for it, so it
            // is already the identity. Any other library target stays refused --
            // String is modelled as a Laurel value rather than a composite, so
            // `as` on it would collide with that encoding.
            if (fqn != null && (ctx.resolver.isThrowableSubtype(fqn)
                    || ctx.resolver.programClassModels.containsKey(fqn))) {
                if (ctx.resolver.isThrowableSubtype(fqn)) {
                    ctx.composites.registerHierarchyUpToThrowable(fqn);
                } else {
                    ctx.composites.referencedCompositeTypes.add(fqn);
                }
                AstNode<StmtExpr> n = push(new StmtExpr.AsType(v,
                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn)))));
                staticTypeOf.put(n, t);
                // A failing cast throws ClassCastException (see explicitImplicitThrows).
                implicitThrows.put(n, new ImplicitThrow(
                        e(LaurelFactory.primitiveOp(new Operation.Not(), e(new StmtExpr.IsType(v,
                                LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn))))))),
                        CLASS_CAST_EXCEPTION));
                return;
            }
            // A functional-interface cast over a lambda/method reference
            // defers the JCLambda refusal on the inner lambda. throwsOn case
            // postconditions unwrap such casts (castWrappedLambdas, read by
            // convertCasePostcondition).
            if (lambdas.containsKey(v)) {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("$castLambda"));
                castWrappedLambdas.put(n, lambdas.get(v));
                defer(n, "Unsupported expression: JCLambda");
                return;
            }
            // Non-exception reference casts refuse with the fully qualified target.
            AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("$cast"));
            staticTypeOf.put(n, t);
            defer(n, "Cast to " + ctx.resolver.qualifyTypeName(t.nameWithScope())
                    + " is not yet supported");
        } else {
            // A plain type test maps onto Laurel's `is`. CompositeModel.emitComposite
            // declares every sort's FULL supertype closure and Laurel keeps a runtime
            // tag per composite, so `is` decides properly for any composite this
            // compilation declares.
            BoxModel.Wrapper box = BoxModel.wrapperOf(tc.type().asInternalName());
            if (!ctx.contractMode && ctx.composites.boxes.models(box) && !patternBindTests.contains(tc)) {
                AstNode<StmtExpr> n = push(new StmtExpr.IsType(v, LaurelFactory.node(new HighType.UserDefined(
                        LaurelFactory.ident(ctx.composites.boxes.declare(box))))));
                bools.add(n);
                return;
            }
            if (fqn == null || patternBindTests.contains(tc)
                    || !(ctx.resolver.isThrowableSubtype(fqn)
                            || ctx.resolver.programClassModels.containsKey(fqn))) {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("$instanceof"));
                bools.add(n);
                defer(n, "instanceof on opaque reference types is not yet supported");
                return;
            }
            TypeRef vt = staticTypeOf.get(v);
            String vfqn = vt == null ? null : resolveFqn(vt);
            if (vfqn != null && !vfqn.equals("java.lang.Object") && !ctx.resolver.isInterface(vfqn)
                    && !ctx.resolver.isInterface(fqn) && ctx.resolver.superclassOf(vfqn) != null
                    && !ctx.resolver.isSubclassOf(vfqn, fqn) && !ctx.resolver.isSubclassOf(fqn, vfqn)) {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralBool(false));
                bools.add(n);
                return;
            }
            String enclosing = enclosingInstanceFqn(v);
            if (enclosing != null && ctx.resolver.isSubclassOf(enclosing, fqn)) {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralBool(true));
                bools.add(n);
                return;
            }
            if (ctx.resolver.isThrowableSubtype(fqn)) {
                ctx.composites.registerHierarchyUpToThrowable(fqn);
            } else {
                ctx.composites.referencedCompositeTypes.add(fqn);
            }
            AstNode<StmtExpr> n = push(new StmtExpr.IsType(v,
                    LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn)))));
            bools.add(n);
        }
    }

    private String enclosingInstanceFqn(AstNode<StmtExpr> v) {
        if (isInstance && cm != null && v.val() instanceof StmtExpr.Var(Variable.Local l)
                && l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING)) {
            return JavaNames.dottedFqn(cm.thisClass().asInternalName());
        }
        if (v.val() instanceof StmtExpr.Var(Variable.Field f) && f.fieldName().text().startsWith("this$")
                && enclosingInstanceFqn(f.target()) != null) {
            TypeRef t = staticTypeOf.get(v);
            return t == null ? null : resolveFqn(t);
        }
        return null;
    }

    /** Resolve a class TypeRef to an fqn: program classes by name, library classes by descriptor. */
    String resolveFqn(TypeRef t) {
        if (t.kind() != TypeRef.Kind.CLASS) {
            return null;
        }
        String qualified = ctx.resolver.qualifyTypeName(t.nameWithScope(), t.fqnFromContext());
        if (qualified.indexOf('.') >= 0 && ctx.resolver.knownClasses.containsKey(qualified)) {
            return qualified;
        }
        // Library class: the descriptor is authoritative for the fqn.
        return t.fqnFromContext() != null ? t.fqnFromContext() : qualified;
    }

    void stackOp(java.lang.classfile.instruction.StackInstruction sk, Instruction in) {
        switch (sk.opcode()) {
            case DUP, DUP_X1, DUP_X2 -> {
                AstNode<StmtExpr> top = stack.peek();
                int depth = sk.opcode() == java.lang.classfile.Opcode.DUP ? 0
                        : (sk.opcode() == java.lang.classfile.Opcode.DUP_X1 ? 1 : 2);
                arrays.refuseValueUse(depth);
                insertDup(top, depth);
            }
            case DUP2, DUP2_X1, DUP2_X2 -> {
                AstNode<StmtExpr> top = stack.peek();
                // A category-2 value (long/double) is ONE node here, so the
                // single-node duplication is exact. Two category-1 values
                // would need two nodes duplicated; refuse rather than
                // silently corrupt the simulated stack.
                String d = descOf.get(top);
                if (!"J".equals(d) && !"D".equals(d)) {
                    throw new JavaViolationException(
                            "Unsupported stack shape: " + sk.opcode() + " over a non-long/double value");
                }
                int depth = sk.opcode() == java.lang.classfile.Opcode.DUP2 ? 0
                        : (sk.opcode() == java.lang.classfile.Opcode.DUP2_X1 ? 1 : 2);
                arrays.refuseValueUse(depth);
                insertDup(top, depth);
            }
            case SWAP -> {
                AstNode<StmtExpr> a = stack.pop(), b = stack.pop();
                stack.push(a);
                stack.push(b);
            }
            case POP -> {
                AstNode<StmtExpr> v = stack.pop();
                if (callNodes.contains(v)) {
                    addStmt(v.val());
                } else {
                    SpecStmt spec = specStmts.get(v);
                    if (spec != null && spec.name().equals("throwsOn")) {
                        // The completed throwsOn(...).x(...) chain statement:
                        // emit the marker the driver's contract extraction
                        // consumes (the chain is one source statement).
                        boolean hasLambda = spec.lambdaArg() != null
                                || spec.links().stream().anyMatch(l -> l.lambdaArg() != null);
                        AstNode<StmtExpr> raw = e(new StmtExpr.LiteralString("$spec$throwsOn"));
                        AstNode<StmtExpr> placed = ctx.placeStatement(raw,
                                stmtRange(pos, hasLambda));
                        stmts.add(placed);
                        specStmts.put(placed, spec);
                    }
                }
            }
            case POP2 -> {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
            default -> todo(in.opcode().toString());
        }
    }

    void insertDup(AstNode<StmtExpr> top, int depth) {
        if (stack.isEmpty()) {
            stack.push(top);
            return;
        }
        stack.pop();
        @SuppressWarnings("unchecked")
        AstNode<StmtExpr>[] below = new AstNode[depth];
        for (int i = 0; i < depth; i++) {
            below[i] = stack.isEmpty() ? e(new StmtExpr.LiteralString("?")) : stack.pop();
        }
        stack.push(top);
        for (int i = depth - 1; i >= 0; i--) {
            stack.push(below[i]);
        }
        stack.push(top);
    }

    // ================= Part 3: calls, fields, indy, peepholes =================

    List<AstNode<StmtExpr>> popArgs(int n) {
        @SuppressWarnings("unchecked")
        AstNode<StmtExpr>[] a = new AstNode[n];
        for (int i = n - 1; i >= 0; i--) {
            a[i] = stack.isEmpty() ? e(new StmtExpr.LiteralString("?")) : stack.pop();
        }
        return new ArrayList<>(java.util.Arrays.asList(a));
    }

    /**
     * Coerce a call ARGUMENT to its parameter type: boolean
     * coercion, and the null-argument cast that then refuses (exception target -> NullLiteralExpr refusal; other reference
     * target -> the cast refusal).
     */
    AstNode<StmtExpr> coerceArg(AstNode<StmtExpr> v, String desc) {
        v = coerce(v, desc);
        if (isNullMarker(v) && (desc.startsWith("L") || desc.startsWith("["))) {
            TypeRef t = TypeRef.ofDescriptor(desc);
            String fqn = t.kind() == TypeRef.Kind.CLASS ? resolveFqn(t) : null;
            if (fqn != null && ctx.resolver.isThrowableSubtype(fqn)) {
                defer(v, "Unsupported expression: NullLiteralExpr");
            } else if (t.kind() == TypeRef.Kind.CLASS) {
                defer(v, "Cast to " + ctx.resolver.qualifyTypeName(t.nameWithScope())
                        + " is not yet supported");
            } else {
                defer(v, "Unsupported expression: NullLiteralExpr");
            }
            return v;
        }
        // byte/char/short narrowing casts on arguments become cast-helper
        // calls: a narrowing store goes through the integer cast helper.
        String narrow = switch (desc) {
            case "B" -> "int8";
            case "C" -> "char";
            case "S" -> "int16";
            default -> null;
        };
        if (narrow == null || desc.equals(descOf.get(v))) {
            return v;
        }
        HighType targetType = narrow.equals("char")
                ? new HighType.UserDefined(LaurelFactory.ident("char"))
                : new HighType.UserDefined(LaurelFactory.ident(narrow));
        HighType sourceType = inferCastSourceType(v);
        String sourceName = LaurelFactory.laurelTypeName(sourceType);
        String helperName = "java?cast_to_" + narrow + "_from_" + sourceName;
        ctx.lf.registerCastHelper(helperName, sourceName, sourceType, narrow, targetType);
        return e(new StmtExpr.StaticCall(LaurelFactory.ident(helperName), List.of(v), List.of()));
    }

    boolean isNullMarker(AstNode<StmtExpr> v) {
        return v.val() instanceof StmtExpr.LiteralString s && s.value().equals("$null");
    }

    /** The receiver's declared static type as an fqn, or null. */
    String staticTypeFqnOf(AstNode<StmtExpr> receiver) {
        if (receiver.val() instanceof StmtExpr.Var var
                && var.var_() instanceof Variable.Local l
                && l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING)) {
            return ctx.currentClassFqn;
        }
        TypeRef t = staticTypeOf.get(receiver);
        if (t == null || t.kind() != TypeRef.Kind.CLASS) {
            return null;
        }
        return ctx.resolver.qualifyTypeName(t.nameWithScope(), t.fqnFromContext());
    }

    /** The thrown expression's static type fqn, or null. */
    String thrownStaticTypeFqn(AstNode<StmtExpr> thrown) {
        if (thrown.val() instanceof StmtExpr.New nw) {
            return nw.ref().text();                     // allocation: the sort IS the fqn
        }
        if (thrown.val() instanceof StmtExpr.AsType as
                && as.targetType().val() instanceof HighType.UserDefined u) {
            return u.name().text();
        }
        TypeRef t = staticTypeOf.get(thrown);
        if (t != null && t.kind() == TypeRef.Kind.CLASS) {
            return resolveFqn(t);
        }
        return null;
    }

    // --- INVOKE ---

    static final Set<String> HEADER_SPEC_NAMES = Set.of(
            "precondition", "postcondition", "invariant", "decreases", "reads", "modifies");

    /** One real (non-capture) parameter of a spec lambda: LVT name + descriptor. */
    record LambdaBinder(String name, String desc) {
    }

    /**
     * Throw-coverage check over a Laurel-thrown value: a throw that no enclosing
     * catch arm covers must fit under the declared exceptional exit, if the
     * method declares one. With no {@code throws} clause an uncovered throw is
     * accepted: Laurel asserts that it is never reached (an error only if it
     * can actually happen), which is how an unchecked Java exception that
     * escapes a method is checked.
     */
    void checkThrowCoverage(String thrownFqn, AstNode<StmtExpr> thrown) {
        if (ctx.method.info.quiet) {
            return;
        }
        if (thrownFqn == null || !ctx.resolver.isThrowableSubtype(thrownFqn)) {
            throw new JavaViolationException(
                    "Unsupported thrown expression type: " + displayOf(thrown));
        }
        boolean caughtLocally = ctx.method.enclosingCatchTypes.stream().anyMatch(arms ->
                arms.stream().anyMatch(c -> ctx.resolver.isSubclassOf(thrownFqn, c)));
        if (caughtLocally || ctx.method.throwsJoin == null || ctx.method.throwsInferred) {
            return;
        }
        if (!ctx.resolver.isSubclassOf(thrownFqn, ctx.method.throwsJoin)) {
            throw new JavaViolationException(
                    "thrown type '" + thrownFqn
                            + "' is not covered by the declared throws clause ('"
                            + ctx.method.throwsJoin + "')");
        }
    }

    /** Best-effort rendering of a thrown value for the refusal message. */
    private String displayOf(AstNode<StmtExpr> n) {
        TypeRef t = staticTypeOf.get(n);
        if (t != null) {
            return t.display();
        }
        if (n.val() instanceof StmtExpr.Var var
                && var.var_() instanceof org.strata.jverify.laurel.Variable.Local loc) {
            return loc.name().text();
        }
        if (n.val() instanceof StmtExpr.New nw) {
            return "new " + nw.ref().text() + "()";
        }
        return n.val().getClass().getSimpleName();
    }

    /** The lambda impl method's body ingredients (its own CFG, locals, and CRT). */
    record ImplShape(Locals locals, Map<Integer, String> slotName,
                     Map<Integer, String> slotDesc, List<CodeElement> seq,
                     List<CrtRange> crt, ControlFlowGraph cfg) {
    }

    // --- Fields (GETFIELD / PUTFIELD / GETSTATIC / PUTSTATIC: FieldAccessTranslator) ---

    // --- invokedynamic (LambdaMetafactory / StringConcatFactory) ---

    // --- Peepholes (tryArrayInit / tryCompoundAssign / tryBooleanRegion) ---

    int tryArrayInit(List<CodeElement> seq, List<Integer> idxs, int k) {
        if (!(seq.get(k) instanceof Instruction na)) {
            return 0;
        }
        String needleForError;
        if (na instanceof java.lang.classfile.instruction.NewPrimitiveArrayInstruction
                || na instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction) {
            needleForError = null;
        } else {
            return 0;
        }
        // javac's array for a varargs call with no variable arguments is not
        // in the source: it keeps the deferred refusal it had before the
        // array model, so array-free programs translate as without contracts.
        if (na instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction ra
                && arrays.enabled() && isEmptyVarargsArray(seq, k, ra)) {
            pushArrayCreationMarker();
            return 1;
        }
        // Under the array model a primitive initializer translates as the
        // allocation plus one store per element; one nested in a reference
        // array's initializer still folds into that initializer's refusal.
        if (na instanceof java.lang.classfile.instruction.NewPrimitiveArrayInstruction
                && referenceInitDepth == 0 && arrays.enabled()) {
            return 0;
        }
        if (k + 1 >= seq.size() || !isDup(seq.get(k + 1)) || stack.isEmpty()) {
            return 0;
        }
        boolean referenceInit = na instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction;
        if (referenceInit) {
            referenceInitDepth++;
        }
        try {
            return foldArrayInit(seq, idxs, k);
        } finally {
            if (referenceInit) {
                referenceInitDepth--;
            }
        }
    }

    /** Nesting depth of reference-array initializers being folded by {@link #tryArrayInit}. */
    private int referenceInitDepth;

    /**
     * javac's empty varargs array: an {@code anewarray} of the literal count
     * 0 whose next instruction is a call taking it as its last parameter.
     */
    private boolean isEmptyVarargsArray(List<CodeElement> seq, int k,
                                        java.lang.classfile.instruction.NewReferenceArrayInstruction ra) {
        if (stack.isEmpty() || !(stack.peek().val() instanceof StmtExpr.LiteralInt n) || n.value() != 0) {
            return false;
        }
        int j = k + 1;
        while (j < seq.size() && seq.get(j) instanceof java.lang.classfile.instruction.LabelTarget) {
            j++;
        }
        if (j >= seq.size() || !(seq.get(j) instanceof java.lang.classfile.instruction.InvokeInstruction inv)) {
            return false;
        }
        var params = inv.typeSymbol().parameterList();
        return !params.isEmpty() && params.getLast().equals(ra.componentType().asSymbol().arrayType());
    }

    /** The array creation the model does not translate: a marker refusing where it is placed. */
    private void pushArrayCreationMarker() {
        stack.pop();
        defer(push(new StmtExpr.LiteralString("$newArray")), "Unsupported expression: ArrayCreationExpr");
    }

    private int foldArrayInit(List<CodeElement> seq, List<Integer> idxs, int k) {
        var elems = new ArrayList<AstNode<StmtExpr>>();
        int p = k + 1;
        while (p + 1 < seq.size() && isDup(seq.get(p))
                && ClassfileQueries.isIntConst(seq.get(p + 1), elems.size())) {
            int j = p + 2;
            while (j < seq.size()
                    && !(seq.get(j) instanceof java.lang.classfile.instruction.ArrayStoreInstruction)) {
                pos = seqAt(idxs, j);
                int c;
                if ((c = tryArrayInit(seq, idxs, j)) > 0) {
                    j += c;
                    continue;
                }
                if ((c = tryCompoundAssign(seq, idxs, j)) > 0) {
                    j += c;
                    continue;
                }
                translate((Instruction) seq.get(j));
                j++;
            }
            if (j >= seq.size() || stack.isEmpty()) {
                return 0;
            }
            elems.add(stack.pop());
            p = j + 1;
        }
        if (elems.isEmpty()) {
            return 0;
        }
        stack.pop();   // the size (implied by the initializer)
        // Array creation with initializer: the marker refuses at placement,
        // reported at the FIRST element's text (see refuseNode).
        AstNode<StmtExpr> marker = push(new StmtExpr.LiteralString("$arrayInit"));
        arrayInitNeedles.put(marker, renderNeedle(elems.get(0)));
        return p - k;
    }

    /** Array-initializer markers -> the first element's rendering (pointRangeAt needle). */
    final Map<AstNode<StmtExpr>, String> arrayInitNeedles = new IdentityHashMap<>();

    /** The pointRangeAt needle: the anchor expression as source text renders it. */
    private String renderNeedle(AstNode<StmtExpr> n) {
        return switch (n.val()) {
            case StmtExpr.LiteralInt i -> String.valueOf(i.value());
            case StmtExpr.LiteralBool b -> String.valueOf(b.value());
            case StmtExpr.LiteralString s -> "\"" + s.value() + "\"";
            case StmtExpr.Var v when v.var_() instanceof Variable.Local l ->
                    l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING) ? "this" : l.name().text();
            default -> "";
        };
    }

    static boolean isDup(CodeElement e) {
        return e instanceof java.lang.classfile.instruction.StackInstruction s
                && s.opcode() == java.lang.classfile.Opcode.DUP;
    }

    static boolean isDup2(CodeElement e) {
        return e instanceof java.lang.classfile.instruction.StackInstruction s
                && s.opcode() == java.lang.classfile.Opcode.DUP2;
    }

    int tryCompoundAssign(List<CodeElement> seq, List<Integer> idxs, int k) {
        if (!(seq.get(k) instanceof Instruction in)) {
            return 0;
        }
        if (isDup2(in) && k + 1 < seq.size()
                && seq.get(k + 1) instanceof java.lang.classfile.instruction.ArrayLoadInstruction
                && stack.size() >= 2) {
            if (arrays.enabled()) {
                // a[i] op= rhs / a[i]++: duplicate the side-effect-free array
                // and index so the load and the store translate normally.
                arrays.duplicateIndexedPair();
                return 1;
            }
            // a[i] op= rhs: arrays refuse as assignment targets.
            throw new JavaViolationException("Unsupported variable expression: ArrayAccessExpr");
        }
        if (isDup(in) && k + 1 < seq.size()
                && seq.get(k + 1) instanceof java.lang.classfile.instruction.FieldInstruction gf
                && gf.opcode() == java.lang.classfile.Opcode.GETFIELD && !stack.isEmpty()) {
            // Value-postfix form (`y = c.n++`): dup_x1 right after the
            // getfield -> decline so the ordinary translation absorbs it.
            if (k + 2 < seq.size()
                    && seq.get(k + 2) instanceof java.lang.classfile.instruction.StackInstruction sx
                    && (sx.opcode() == java.lang.classfile.Opcode.DUP_X1
                            || sx.opcode() == java.lang.classfile.Opcode.DUP_X2)) {
                return 0;
            }
            int j = k + 2;
            while (j < seq.size()
                    && !(seq.get(j) instanceof java.lang.classfile.instruction.FieldInstruction f
                            && f.opcode() == java.lang.classfile.Opcode.PUTFIELD)) {
                j++;
            }
            if (j >= seq.size()
                    || !sameField(gf, (java.lang.classfile.instruction.FieldInstruction) seq.get(j))) {
                return 0;
            }
            if (!(seq.get(j - 1) instanceof java.lang.classfile.instruction.OperatorInstruction bop)) {
                return 0;
            }
            Operation aop = compoundOp(bop.opcode());
            AstNode<StmtExpr> recv = stack.pop();
            translateSpan(seq, idxs, k + 2, j - 1);
            if (stack.isEmpty()) {
                return 0;
            }
            AstNode<StmtExpr> rhs = stack.pop();
            boolean superWrite = fieldAccess.isSuperFieldRead(gf, recv);
            fieldAccess.declareAccessedField(recv, gf.name().stringValue());
            if (superWrite) {
                throw new JavaViolationException("Unsupported expression: SuperExpr");
            }
            var target = new AstNode<Variable>(new Variable.Field(recv,
                    LaurelFactory.ident(gf.name().stringValue())), SourceMap.UNKNOWN_RANGE);
            addStmt(new StmtExpr.CompoundAssign(aop, target,
                    coerce(rhs, gf.typeSymbol().descriptorString())));
            return (j + 1) - k;
        }
        return 0;
    }

    /** Compound-assignment operator support, refusing the rest with their operator names. */
    private Operation compoundOp(java.lang.classfile.Opcode op) {
        return switch (op) {
            case IADD, LADD, FADD, DADD -> new Operation.Add();
            case ISUB, LSUB, FSUB, DSUB -> new Operation.Sub();
            case IMUL, LMUL, FMUL, DMUL -> new Operation.Mul();
            case IDIV, LDIV, FDIV, DDIV -> new Operation.DivT();
            case IREM, LREM, FREM, DREM -> new Operation.ModT();
            case IAND, LAND -> throw new JavaViolationException(
                    "Unsupported compound assignment: BINARY_AND");
            case IOR, LOR -> throw new JavaViolationException(
                    "Unsupported compound assignment: BINARY_OR");
            case IXOR, LXOR -> throw new JavaViolationException(
                    "Unsupported compound assignment: XOR");
            case ISHL, LSHL -> throw new JavaViolationException(
                    "Unsupported compound assignment: LEFT_SHIFT");
            case ISHR, LSHR -> throw new JavaViolationException(
                    "Unsupported compound assignment: SIGNED_RIGHT_SHIFT");
            case IUSHR, LUSHR -> throw new JavaViolationException(
                    "Unsupported compound assignment: UNSIGNED_RIGHT_SHIFT");
            default -> throw new JavaViolationException("Unsupported compound assignment: " + op);
        };
    }

    static boolean sameField(java.lang.classfile.instruction.FieldInstruction a,
                             java.lang.classfile.instruction.FieldInstruction b) {
        return a.owner().asInternalName().equals(b.owner().asInternalName())
                && a.name().equals(b.name());
    }

    /** Boolean materialization region ({@code &&} folds to $andThen). */
    int tryBooleanRegion(List<CodeElement> seq, int k,
                         java.lang.classfile.instruction.BranchInstruction first) {
        java.lang.classfile.Label lfalse = first.target();
        int p = -1;
        for (int j = k; j + 4 < seq.size(); j++) {
            if (ClassfileQueries.isIntConst(seq.get(j), 1)
                    && seq.get(j + 1) instanceof java.lang.classfile.instruction.BranchInstruction g
                    && g.opcode() == java.lang.classfile.Opcode.GOTO
                    && seq.get(j + 2) instanceof java.lang.classfile.instruction.LabelTarget f
                    && ClassfileQueries.sameLabel(f.label(), lfalse)
                    && ClassfileQueries.isIntConst(seq.get(j + 3), 0)
                    && seq.get(j + 4) instanceof java.lang.classfile.instruction.LabelTarget e2
                    && ClassfileQueries.sameLabel(e2.label(), g.target())) {
                p = j;
                break;
            }
            CodeElement el = seq.get(j);
            if (el instanceof java.lang.classfile.instruction.BranchInstruction b) {
                if (!ClassfileQueries.isConditionalBranch(b.opcode())
                        || !ClassfileQueries.sameLabel(b.target(), lfalse)) {
                    return 0;
                }
            } else if (el instanceof java.lang.classfile.instruction.LabelTarget) {
                return 0;
            } else if (!(el instanceof java.lang.classfile.instruction.ConstantInstruction
                    || el instanceof LoadInstruction
                    || el instanceof java.lang.classfile.instruction.OperatorInstruction
                    || el instanceof java.lang.classfile.instruction.ConvertInstruction
                    || el instanceof java.lang.classfile.instruction.FieldInstruction
                    || el instanceof java.lang.classfile.instruction.InvokeInstruction
                    || el instanceof java.lang.classfile.instruction.ArrayLoadInstruction)) {
                return 0;
            }
        }
        if (p < 0) {
            return 0;
        }
        var guards = new ArrayList<AstNode<StmtExpr>>();
        // Everything after the first test runs only when the earlier tests pass.
        boolean conditional = false;
        try {
            for (int j = k; j < p; j++) {
                CodeElement el = seq.get(j);
                if (el instanceof java.lang.classfile.instruction.BranchInstruction b
                        && ClassfileQueries.isConditionalBranch(b.opcode())) {
                    guards.add(materializeComparison(b.opcode()));
                    if (!conditional) {
                        conditional = true;
                        enterConditional();
                    }
                } else {
                    translate((Instruction) el);
                }
            }
        } finally {
            if (conditional) {
                exitConditional();
            }
        }
        if (guards.isEmpty()) {
            return 0;
        }
        AstNode<StmtExpr> result = guards.get(0);
        for (int i = 1; i < guards.size(); i++) {
            // `&&` guards fold left to right into AndThen.
            result = e(LaurelFactory.primitiveOp(new Operation.AndThen(), result, guards.get(i)));
        }
        bools.add(result);
        stack.push(result);
        return (p + 5) - k;
    }

}

package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.IncrDecrMode;
import org.strata.jverify.laurel.IncrDecrOp;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.GotoDestination;
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
import java.util.HashSet;
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
     * such an arm (ValueFolder). The box model refuses to
     * unbox them (InvokeTranslator), as a Laurel composite value is
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
                        String samName, String samDesc, String instantiatedDesc,
                        List<TypeRef> captureTypes) {
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
     * Refuse a specification argument that mentions an array allocation's
     * local: the allocation ran as body statements before the specification
     * was recognized.
     */
    void refuseAllocTempMention(AstNode<StmtExpr> n) {
        if (allocTemps.isEmpty() || n == null) {
            return;
        }
        if (isStackRead(n)) {
            AstNode<StmtExpr> o = origin(n);
            if (o != n) {
                refuseAllocTempMention(o);
            }
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
        if (lit instanceof StmtExpr.Call sc && sc.receiver().isEmpty()) {
            ctx.method.statementNodes.add(sc.arguments().get(0));
        }
        return lit;
    }

    AstNode<StmtExpr> push(StmtExpr v) {
        AstNode<StmtExpr> n = e(v);
        stack.push(n);
        return n;
    }

    private final Map<StmtExpr, AstNode<StmtExpr>> pushOf = new IdentityHashMap<>();
    private final Map<StmtExpr, AstNode<StmtExpr>> spillOrigin = new IdentityHashMap<>();
    boolean spilling = true;
    /**
     * When set, a local first stored without a hoisted declaration is declared here
     * instead, at its value's type, and placed at the method top with the hoisted
     * ones ({@link BlockEmitter}): its uses may lie in other labelled blocks than the
     * store.
     */
    List<AstNode<StmtExpr>> lateDecls;
    /** The type a local with no recorded type was declared at, at its first store. */
    final Map<String, TypeRef> lateDeclTypes = new HashMap<>();
    /**
     * The locals javac saves a catch-all handler's exception in to rethrow it: they are
     * declared at the handler with the type Laurel infers for the exception, and their
     * rethrow is not a new throw.
     */
    final Set<String> caughtAllSlots = new HashSet<>();
    private final Set<AstNode<StmtExpr>> rethrows = Collections.newSetFromMap(new IdentityHashMap<>());

    static long countStackReads(AstNode<StmtExpr> n) {
        long[] count = {0};
        LaurelTrees.map(n, x -> {
            count[0] += isStackRead(x) ? 1 : 0;
            return x;
        });
        return count[0];
    }

    static boolean isStackRead(AstNode<StmtExpr> n) {
        return n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Stack;
    }

    static AstNode<StmtExpr> stackRead(FileRange src) {
        return new AstNode<>(new StmtExpr.Var(new Variable.Stack(Optional.empty())), src);
    }

    boolean held(AstNode<StmtExpr> n) {
        return switch (n.val()) {
            case StmtExpr.LiteralInt x -> true;
            case StmtExpr.LiteralBool x -> true;
            case StmtExpr.LiteralString x -> true;
            case StmtExpr.LiteralDecimal x -> true;
            case StmtExpr.LiteralBv x -> true;
            case StmtExpr.This x -> true;
            case StmtExpr.All x -> true;
            case StmtExpr.Call c when c.receiver().isEmpty() && c.callee().text().equals("$neg")
                    && c.arguments().size() == 1
                    && held(c.arguments().get(0)) -> true;
            case StmtExpr.Var v when v.var_() instanceof Variable.Stack -> true;
            case StmtExpr.Var v when v.var_() instanceof Variable.Local l
                    && l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING) -> true;
            default -> deferredRefusal.containsKey(n) || ctx.method.statementNodes.contains(n);
        };
    }

    void settle() {
        if (!spilling || stack.isEmpty()) {
            return;
        }
        var entries = new ArrayList<>(stack);
        Collections.reverse(entries);
        spillFrom(entries, 0);
    }

    void settleInto(List<AstNode<StmtExpr>> out) {
        int mark = stmts.size();
        settle();
        out.addAll(stmts.subList(mark, stmts.size()));
        stmts.subList(mark, stmts.size()).clear();
    }

    private void spillFrom(List<AstNode<StmtExpr>> entries, int floor) {
        for (int i = floor; i < entries.size(); i++) {
            AstNode<StmtExpr> n = entries.get(i);
            if (!held(n)) {
                entries.set(i, pushValue(n));
            }
        }
        stack.clear();
        for (AstNode<StmtExpr> entry : entries) {
            stack.push(entry);
        }
    }

    AstNode<StmtExpr> origin(AstNode<StmtExpr> n) {
        AstNode<StmtExpr> o = isStackRead(n) ? spillOrigin.get(n.val()) : null;
        return o == null ? n : origin(o);
    }

    final Set<AstNode<StmtExpr>> implicitGuards = Collections.newSetFromMap(new IdentityHashMap<>());

    boolean isContractStep(AstNode<StmtExpr> s) {
        return isContractStep(s, Set.of());
    }

    private boolean isContractStep(AstNode<StmtExpr> s, Set<String> labels) {
        return switch (s.val()) {
            case StmtExpr.IfThenElse i when implicitGuards.contains(s) -> true;
            case StmtExpr.IfThenElse i -> isContractStep(i.thenBranch(), labels)
                    && i.elseBranch().map(e -> isContractStep(e, labels)).orElse(true);
            case StmtExpr.Block b -> {
                Set<String> inner = b.label().map(l -> {
                    var all = new java.util.HashSet<>(labels);
                    all.add(l);
                    return (Set<String>) all;
                }).orElse(labels);
                yield b.invariants().isEmpty() && b.decreases().isEmpty()
                        && b.statements().stream().allMatch(x -> isContractStep(x, inner));
            }
            case StmtExpr.Goto g -> g.destination() instanceof GotoDestination.BlockEnd && labels.contains(g.target());
            case StmtExpr.Assign a when a.targets().size() == 1
                    && a.targets().get(0).val() instanceof Variable.Declare d -> isMintedName(d.parameter().name().text());
            default -> isPush(s);
        };
    }

    private static boolean isMintedName(String name) {
        return name.startsWith("t$") || name.contains("?");
    }

    List<AstNode<StmtExpr>> contractPrefix(List<AstNode<StmtExpr>> steps) {
        return steps.stream().filter(s -> !implicitGuards.contains(s)).map(this::withoutGuards).toList();
    }

    private AstNode<StmtExpr> withoutGuards(AstNode<StmtExpr> s) {
        return switch (s.val()) {
            case StmtExpr.IfThenElse i when i.elseBranch().isPresent() -> new AstNode<>(new StmtExpr.IfThenElse(
                    i.cond(), withoutGuards(i.thenBranch()), Optional.of(withoutGuards(i.elseBranch().get()))),
                    s.source());
            case StmtExpr.Block b -> new AstNode<>(new StmtExpr.Block(contractPrefix(b.statements()), b.label(),
                    b.invariants(), b.decreases()), s.source());
            default -> s;
        };
    }

    static AstNode<StmtExpr> pushedBefore(AstNode<StmtExpr> pushed, AstNode<StmtExpr> value) {
        return withPrefix(List.of(new AstNode<>(new StmtExpr.Assign(List.of(new AstNode<Variable>(
                new Variable.Stack(Optional.empty()), pushed.source())), pushed), pushed.source())), value);
    }

    static AstNode<StmtExpr> withPrefix(List<AstNode<StmtExpr>> prefix, AstNode<StmtExpr> value) {
        if (prefix.isEmpty()) {
            return value;
        }
        var all = new ArrayList<>(prefix);
        if (value.val() instanceof StmtExpr.Block b && b.label().isEmpty() && b.invariants().isEmpty()
                && b.decreases().isEmpty()) {
            all.addAll(b.statements());
        } else {
            all.add(value);
        }
        return new AstNode<>(new StmtExpr.Block(all, Optional.empty(), List.of(), Optional.empty()), value.source());
    }

    static boolean isPush(AstNode<StmtExpr> s) {
        return s.val() instanceof StmtExpr.Assign a && !a.targets().isEmpty()
                && a.targets().stream().allMatch(t -> t.val() instanceof Variable.Stack);
    }

    AstNode<StmtExpr> unspill(AstNode<StmtExpr> n) {
        if (isStackRead(n)) {
            AstNode<StmtExpr> origin = spillOrigin.get(n.val());
            AstNode<StmtExpr> push = pushOf.get(n.val());
            if (origin == null || push == null || !stmts.removeIf(x -> x == push)) {
                return n;
            }
            return unspill(origin);
        }
        AstNode<StmtExpr> done = LaurelTrees.map(n, x -> isStackRead(x) ? unspill(x) : x);
        if (done != n) {
            transfer(n, done);
        }
        return done;
    }

    AstNode<StmtExpr> pushValue(AstNode<StmtExpr> n) {
        if (isStackRead(n)) {
            return n;
        }
        addStmt(new StmtExpr.Assign(List.of(new AstNode<Variable>(new Variable.Stack(Optional.empty()), n.source())), n));
        AstNode<StmtExpr> read = stackRead(n.source());
        transfer(n, read);
        pushOf.put(read.val(), stmts.get(stmts.size() - 1));
        spillOrigin.put(read.val(), n);
        return read;
    }

    void copyFacts(AstNode<StmtExpr> from, AstNode<StmtExpr> to) {
        transfer(from, to);
    }

    private void transfer(AstNode<StmtExpr> from, AstNode<StmtExpr> to) {
        String d = descOf.get(from);
        if (d != null) {
            descOf.put(to, d);
        }
        TypeRef t = staticTypeOf.get(from);
        if (t != null) {
            staticTypeOf.put(to, t);
        }
        for (Set<AstNode<StmtExpr>> facts : List.of(bools, typeVarValues, impureValues, stringLiterals,
                stringConcatResults, untypedLoads, callNodes, heapReadValues, unboundedResults)) {
            if (facts.contains(from)) {
                facts.add(to);
            }
        }
        String key = calleeKeys.get(from.val());
        if (key != null) {
            calleeKeys.put(to.val(), key);
        }
        if (erasedResults.contains(from.val())) {
            erasedResults.add(to.val());
        }
        CalleeRef ref = calleeModels.get(from.val());
        if (ref != null) {
            calleeModels.put(to.val(), ref);
        }
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
        String key = calleeKeys.get(n.val());
        if (key != null && ctx.contracts.allocatesKey(key)) {
            throw new JavaViolationException(key.endsWith("_valueOf")
                    ? "boxing inside a contract is not yet supported"
                    : "a call that allocates inside a contract is not yet supported");
        }
    }

    void checkContractCode(AstNode<StmtExpr> n) {
        switch (n.val()) {
            case StmtExpr.Block b when b.label().isEmpty() -> b.statements().forEach(this::checkContractCode);
            case StmtExpr.IfThenElse i -> {
                checkContractArg(i.cond());
                checkContractCode(i.thenBranch());
                i.elseBranch().ifPresent(this::checkContractCode);
            }
            case StmtExpr.Assign a when isPush(n) -> checkContractArg(a.value());
            default -> checkContractArg(n);
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
            case StmtExpr.Call n -> {
                n.receiver().ifPresent(out::add);
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
            implicitGuards.add(placed);
        }
        AstNode<StmtExpr> placed = place(raw, range);
        stmtSeq.put(placed, seqIdx);
        stmts.add(placed);
        noteStaticEffects(raw, range);
    }

    /** Record, by source line, the static reads of {@code stmt} and the calls in it that may change a static. */
    private void noteStaticEffects(AstNode<StmtExpr> stmt, FileRange range) {
        if (range == SourceMap.UNKNOWN_RANGE || ctx.src.uri == null) {
            return;
        }
        int line = ctx.src.positionRange(range).start().line();
        if (StaticFields.readsGlobal(stmt)) {
            ctx.staticNotes.recordStaticRead(ctx.src.uri, line);
        }
        noteOpaqueCalls(stmt, line);
    }

    private void noteOpaqueCalls(AstNode<StmtExpr> n, int line) {
        String callee = switch (n.val()) {
            case StmtExpr.Call c -> c.callee().text();
            default -> null;
        };
        if (callee != null && !LaurelFactory.isOperationProcName(callee)
                && LaurelFactory.arithHelperOp(callee) == null) {
            if (callee.endsWith("::" + StaticFields.CLINIT_PROCEDURE)) {
                String owner = callee.substring(0, callee.length() - 2 - StaticFields.CLINIT_PROCEDURE.length());
                ctx.staticNotes.recordCall(ctx.src.uri, line,
                        StaticEffectNotes.describe(StaticEffectNotes.Kind.INITIALIZER, JavaNames.simpleName(owner)),
                        StaticEffectNotes.Kind.INITIALIZER);
            } else {
                CalleeRef ref = calleeModels.get(n.val());
                boolean transparent = ref != null && Declarations.hasAnnotation(ref.method(), "Transparent");
                if (!transparent) {
                    // A static procedure's name is `Owner::method`; an instance call's is the bare method.
                    int sep = callee.lastIndexOf("::");
                    String shown = ref != null ? ref.method().methodName().stringValue()
                            : ClassResolver.demangle(sep < 0 ? callee : callee.substring(sep + 2));
                    var kind = ref != null ? StaticEffectNotes.Kind.BODY : StaticEffectNotes.Kind.NO_BODY;
                    ctx.staticNotes.recordCall(ctx.src.uri, line,
                            StaticEffectNotes.describe(kind, shown + "()"), kind);
                }
            }
        }
        for (AstNode<StmtExpr> c : childrenOf(n.val())) {
            noteOpaqueCalls(c, line);
        }
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

    /**
     * Declared types of a spec lambda's captured parameters, by impl slot,
     * seeded from the capture site's {@code staticTypeOf} (see
     * {@code LambdaHandle.captureTypes}). Only populated on the inner
     * translator of a spec lambda body.
     */
    final Map<Integer, TypeRef> captureTypeOf = new HashMap<>();

    /** Declared TypeRef of a local: generic (LVTT) if known, else erased, else null ("var"). */
    TypeRef localTypeRef(int slot) {
        TypeRef captured = captureTypeOf.get(slot);
        if (captured != null) {
            return captured;
        }
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
            case Float f -> {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("float " + f));
                noteDesc(n, "F");
                defer(n, "Unsupported expression: DoubleLiteralExpr");
            }
            case Double d -> {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("double " + d));
                noteDesc(n, "D");
                defer(n, "Unsupported expression: DoubleLiteralExpr");
            }
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
        if (t == null) {
            t = lateDeclTypes.get(varKey(li.slot()));
        }
        if (caughtAllSlots.contains(varKey(li.slot()))) {
            rethrows.add(n);
        }
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
        if (lateDecls != null && caughtAllSlots.contains(varKey(si.slot()))) {
            String nm = freshName(si.slot());
            caughtAllSlots.add(varKey(si.slot()));
            declared.add(varKey(si.slot()));
            lateDecls.add(e(new StmtExpr.Var(new Variable.Declare(
                    new ParameterOpt(LaurelFactory.ident(nm), Optional.empty())))));
            addStmt(new StmtExpr.Assign(List.of(localVarTarget(si.slot())), val));
            return;
        }
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
        if (declared.add(varKey(si.slot()))) {
            // First store to the slot: a local declaration.
            String nm = localName(si.slot());
            LaurelFactory.refuseReservedName(nm);
            TypeRef t = localTypeRef(si.slot());
            if (lateDecls != null) {
                TypeRef vt = t != null ? t : staticTypeOf.get(val);
                if (vt == null && descOf.get(val) != null) {
                    vt = TypeRef.ofDescriptor(descOf.get(val), false, false);
                }
                if (vt != null) {
                    lateDeclTypes.put(varKey(si.slot()), vt);
                    lateDecls.add(e(new StmtExpr.Var(new Variable.Declare(new ParameterOpt(LaurelFactory.ident(nm),
                            Optional.of(LaurelFactory.node(vt.toLaurel(ctx.typeParams, ctx.methodTypeParams,
                                    ctx.resolver, ctx.composites))))))));
                    addStmt(new StmtExpr.Assign(List.of(localVarTarget(si.slot())), val));
                    return;
                }
            }
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

    /** IINC: a compound-assignment statement. */
    void increment(IncrementInstruction ii) {
        int c = ii.constant();
        addStmt(new StmtExpr.CompoundAssign(c >= 0 ? new Operation.Add() : new Operation.Sub(),
                localVarTarget(ii.slot()), e(new StmtExpr.LiteralInt(Math.abs(c)))));
    }

    // --- Operators (arithmetic, comparison and boolean ops) ---

    /** Binary $op push. */
    void bin(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        push(LaurelFactory.primitiveOp(op, a, b));
    }

    /**
     * Integer (int/long) {@code +}/{@code -}/{@code *}: Java wraps on
     * overflow, JVerify instead demands a proof that the mathematical result
     * fits the operand width — at the operation itself, so an overflow in an
     * intermediate subexpression such as {@code (a + b) / 2} is caught even
     * when the result never reaches an assignment, return or parameter.
     * The op is emitted as a call to a checked helper whose {@code requires}
     * bounds the result; the helper's transparent body keeps the value equal
     * to the mathematical result in every position, including contracts.
     *
     * <p>An {@code @Unbounded} operand opts the whole operation out: its
     * arithmetic is mathematical by declaration, so the plain {@code $op}
     * is emitted and the result stays unbounded ({@link #unboundedResults}).
     */
    void checkedBin(Operation op, String opKey, String symbol, boolean isLong) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        if (isUnboundedInt(a) || isUnboundedInt(b)) {
            unboundedResults.add(push(LaurelFactory.primitiveOp(op, a, b)));
            return;
        }
        String helper = ctx.lf.registerArithHelper(opKey, symbol, op, false, isLong ? "int64" : "int32");
        AstNode<StmtExpr> n = push(LaurelFactory.staticCall(
                LaurelFactory.ident(helper), List.of(a, b), List.of()));
        noteDesc(n, isLong ? "J" : "I");
    }

    /** INEG/LNEG with the overflow obligation of {@link #checkedBin} ({@code -MIN_VALUE} overflows). */
    void checkedNeg(boolean isLong) {
        AstNode<StmtExpr> v = stack.pop();
        // Literal folding: a negated literal folds into the literal itself —
        // except MIN_VALUE, whose negation does not fit the width (the JVM
        // wraps it back to MIN_VALUE); it falls through to the checked helper
        // so the overflow obligation is emitted. javac folds -MIN_VALUE
        // itself, but a literal can reach here from other bytecode producers.
        long min = isLong ? Long.MIN_VALUE : Integer.MIN_VALUE;
        if (v.val() instanceof StmtExpr.LiteralInt li && li.value() != min) {
            push(pinnedLongLiteral(-li.value()));
            return;
        }
        if (isUnboundedInt(v)) {
            unboundedResults.add(push(LaurelFactory.primitiveOp(new Operation.Neg(), v)));
            return;
        }
        String helper = ctx.lf.registerArithHelper("neg", "-", new Operation.Neg(), true,
                isLong ? "int64" : "int32");
        AstNode<StmtExpr> n = push(LaurelFactory.staticCall(
                LaurelFactory.ident(helper), List.of(v), List.of()));
        noteDesc(n, isLong ? "J" : "I");
    }

    /**
     * Results of plain {@code $add}/{@code $sub}/{@code $mul}/{@code $neg}
     * emitted because an operand was {@code @Unbounded}: they stay in the
     * mathematical domain, so further operations on them are not
     * overflow-checked either. Identity-keyed, like the other node maps.
     */
    final java.util.Set<AstNode<StmtExpr>> unboundedResults =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * True when the operand is known to verify in the unbounded integer
     * domain: a value whose declared type carries {@code @Unbounded}, a
     * range cast to {@code int}/{@code nat}, or arithmetic on such values.
     * Conservative in the other direction — anything whose unbounded type
     * the frontend cannot see (e.g. a spec lambda's own {@code @Unbounded}
     * binder, whose annotation javac drops from the synthetic impl) is
     * treated as bounded, which matches the JVM value's actual width; the
     * escape hatch is binding it to an {@code @Unbounded} local first.
     */
    boolean isUnboundedInt(AstNode<StmtExpr> n) {
        AstNode<StmtExpr> origin = isStackRead(n) ? spillOrigin.get(n.val()) : null;
        if (origin != null && origin != n && isUnboundedInt(origin)) {
            return true;
        }
        TypeRef t = staticTypeOf.get(n);
        if (t != null && t.isUnbounded()) {
            return true;
        }
        if (n.val() instanceof StmtExpr.Call call && call.receiver().isEmpty()) {
            String callee = call.callee().text();
            if (callee.startsWith("java?cast_to_int_from_") || callee.startsWith("java?cast_to_nat_from_")) {
                return true;
            }
        }
        return unboundedResults.contains(n);
    }

    /**
     * Integer (int/long) division or remainder: throws ArithmeticException on a
     * zero divisor. Recorded as an implicit throw on the operation's node; see
     * {@link #explicitImplicitThrows}.
     */
    void intDivRem(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        // The quotient/remainder of @Unbounded operands stays in the
        // mathematical domain, like the checked ops' opt-out (checkedBin);
        // without this, `n / 2 + 1` on an @Unbounded n picks up an int32
        // overflow obligation.
        boolean unbounded = isUnboundedInt(a) || isUnboundedInt(b);
        stack.push(a);
        stack.push(b);
        AstNode<StmtExpr> divisor = stack.peek();
        bin(op);
        if (unbounded) {
            unboundedResults.add(stack.peek());
        }
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
     * The explicit {@code if (condition) { throw new E }} statement to place
     * immediately before statement {@code root}, for the implicit throw of the
     * operation {@code root} pushes. Each implicit throw is decided once, here. Laurel then
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
     *   <li>when the exception would leave a method whose {@code throws}
     *       clause does not cover it, and no enclosing {@code catch} does
     *       (Laurel rejects that escape statically).</li>
     * </ul>
     */
    List<AstNode<StmtExpr>> explicitImplicitThrows(AstNode<StmtExpr> root) {
        if (ctx.method.inContract || ctx.method.pure || inConditional()
                || root.val() instanceof StmtExpr.Assert || root.val() instanceof StmtExpr.Assume) {
            return List.of();
        }
        ImplicitThrow found = implicitThrows.remove(root.val() instanceof StmtExpr.Assign a ? a.value() : root);
        var out = new ArrayList<AstNode<StmtExpr>>();
        if (found != null && mayThrowHere(found.exceptionFqn())) {
            ImplicitThrow t = found;
            ctx.composites.registerHierarchyUpToThrowable(t.exceptionFqn());
            AstNode<StmtExpr> exception = e(new StmtExpr.New(LaurelFactory.ident(t.exceptionFqn()), List.of()));
            AstNode<StmtExpr> pushed = e(new StmtExpr.Assign(List.of(new AstNode<Variable>(
                    new Variable.Stack(Optional.empty()), exception.source())), exception));
            AstNode<StmtExpr> thrown = e(new StmtExpr.Throw(stackRead(exception.source())));
            AstNode<StmtExpr> guard = e(new StmtExpr.IfThenElse(t.condition(),
                    e(new StmtExpr.Block(List.of(pushed, thrown), Optional.empty(), List.of(), Optional.empty())),
                    Optional.empty()));
            long reads = countStackReads(t.condition());
            if (reads > 0) {
                var name = new StringBuilder("$permute");
                var targets = new ArrayList<AstNode<Variable>>();
                for (int copy = 0; copy < 2; copy++) {
                    for (int r = 0; r < reads; r++) {
                        name.append('_').append(r);
                        targets.add(new AstNode<>(new Variable.Stack(Optional.empty()), exception.source()));
                    }
                }
                var args = java.util.stream.LongStream.range(0, reads).mapToObj(r -> stackRead(exception.source())).toList();
                AstNode<StmtExpr> dup = e(new StmtExpr.Assign(targets, e(LaurelFactory.staticCall(
                        LaurelFactory.reservedIdent(name.toString()), args, List.of()))));
                guard = e(new StmtExpr.Block(List.of(dup, guard), Optional.empty(), List.of(), Optional.empty()));
            }
            out.add(guard);
        }
        return out;
    }

    /**
     * Whether an implicit {@code exceptionFqn} may leave this point as an
     * explicit throw: the method declares no {@code throws} (the escape is
     * then asserted not to happen), a covering one, or an inferred one
     * (widened to cover it), or an enclosing {@code catch} covers it. Laurel
     * lets an exception leave a method only in those cases.
     *
     * <p>An enclosing {@code catch} arm unrelated to the exception (an
     * {@code IllegalStateException} arm around a division) is no obstacle: Laurel
     * accepts a guard testing the caught exception against an unrelated type
     * and lowers that test to {@code false}, and the handler reads its binding
     * at the arm's type (see {@link #catchCasts}), so the wider join
     * Laurel then types the binding at does not change what the handler sees.
     */
    boolean mayThrowHere(String exceptionFqn) {
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
        return e instanceof StmtExpr.Call c && c.receiver().isEmpty()
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

    void pushCmp3() {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        noteDesc(push(LaurelFactory.staticCall(LaurelFactory.reservedIdent("$cmp3"), List.of(a, b), List.of())), "I");
    }

    void refuseCmp3(String boxType) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        AstNode<StmtExpr> n = push(LaurelFactory.staticCall(
                LaurelFactory.ident("java?cmp3$" + boxType), List.of(a, b), List.of()));
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
        AstNode<StmtExpr> b = origin(it.next());
        AstNode<StmtExpr> a = origin(it.next());
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
                && ctx.composites.boxes.hazard(BoxModel.wrapperOf(desc.substring(1, desc.length() - 1)));
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
        return e(LaurelFactory.primitiveOp(op, a, e(new StmtExpr.LiteralInt(0))));
    }

    private AstNode<StmtExpr> unaryBool(boolean trueWhenNonZero) {
        AstNode<StmtExpr> a = stack.pop();
        Operation op = trueWhenNonZero ? new Operation.Neq() : new Operation.Eq();
        if (isBool(a)) {
            return trueWhenNonZero ? a : LaurelTrees.negate(a);
        }
        return e(LaurelFactory.primitiveOp(op, a, e(new StmtExpr.LiteralInt(0))));
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
     * BlockEmitter.markPatternBindTests. Laurel's {@code is} has
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
                    settle();
                    continue;
                }
            }
            int c;
            if ((c = tryArrayInit(seq, null, k)) > 0) {
                k += c;
                settle();
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
                settle();
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
        settle();
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
        switch (in) {
            case java.lang.classfile.instruction.ConstantInstruction ci -> pushConstant(ci.constantValue());
            case LoadInstruction li -> load(li);
            case StoreInstruction si -> store(si);
            case IncrementInstruction ii -> increment(ii);
            case java.lang.classfile.instruction.OperatorInstruction oi -> {
                operator(oi);
                noteResultKind(oi.typeKind());
            }
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
                // `new` is a first use of the class, before the constructor's
                // arguments are evaluated.
                ctx.statics.initialize(this, JavaNames.dottedFqn(nw.className().asInternalName()), List.of());
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
                    if (!rethrows.contains(origin(v))) {
                        checkThrowCoverage(thrownFqn, v);
                        ctx.composites.registerHierarchyUpToThrowable(thrownFqn);
                    }
                    FileRange range = stmtRange(pos, false);
                    for (AstNode<StmtExpr> t : explicitImplicitThrows(before)) {
                        AstNode<StmtExpr> placed = place(t, range);
                        stmts.add(placed);
                        implicitGuards.add(placed);
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

    /** An int or long operator's result, when it carries no descriptor of its own, gets its kind's. */
    private void noteResultKind(java.lang.classfile.TypeKind kind) {
        if (stack.isEmpty() || descOf.containsKey(stack.peek()) || bools.contains(stack.peek())) {
            return;
        }
        if (kind == java.lang.classfile.TypeKind.INT || kind == java.lang.classfile.TypeKind.LONG) {
            descOf.put(stack.peek(), kind.upperBound().descriptorString());
        }
    }

    void operator(java.lang.classfile.instruction.OperatorInstruction oi) {
        switch (oi.opcode()) {
            case IADD -> checkedBin(new Operation.Add(), "add", "+", false);
            case LADD -> checkedBin(new Operation.Add(), "add", "+", true);
            case ISUB -> checkedBin(new Operation.Sub(), "sub", "-", false);
            case LSUB -> checkedBin(new Operation.Sub(), "sub", "-", true);
            case IMUL -> checkedBin(new Operation.Mul(), "mul", "*", false);
            case LMUL -> checkedBin(new Operation.Mul(), "mul", "*", true);
            case FADD, DADD -> bin(new Operation.Add());
            case FSUB, DSUB -> bin(new Operation.Sub());
            case FMUL, DMUL -> bin(new Operation.Mul());
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
            case INEG -> checkedNeg(false);
            case LNEG -> checkedNeg(true);
            case FNEG, DNEG -> neg();
            case LCMP -> pushCmp3();
            case FCMPL, FCMPG -> refuseCmp3("Float");
            case DCMPL, DCMPG -> refuseCmp3("Double");
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
        noteDesc(stack.peek(), switch (oi.opcode()) {
            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR, INEG -> "I";
            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR, LNEG -> "J";
            case FADD, FSUB, FMUL, FDIV, FREM, FNEG -> "F";
            case DADD, DSUB, DMUL, DDIV, DREM, DNEG -> "D";
            default -> null;
        });
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
        AstNode<StmtExpr> n = push(LaurelFactory.staticCall(
                LaurelFactory.ident(helperName), List.of(operand), List.of()));
        noteDesc(n, ClassfileQueries.convDesc(cv.opcode()));
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
        AstNode<StmtExpr> n = push(LaurelFactory.staticCall(LaurelFactory.ident(helperName), List.of(operand), List.of()));
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
        if (operand.val() instanceof StmtExpr.Call call && call.receiver().isEmpty()
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
                if (ctx.contracts.compositeSortOf(fqn) != null) {
                    typeVarValues.remove(v);
                    typeVarValues.remove(origin(v));
                    staticTypeOf.put(origin(v), t);
                }
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
            String castSort = ctx.contracts.wrapperSortOf(fqn);
            if (castSort != null) {
                ctx.composites.referencedCompositeTypes.add(castSort);
                var castType = LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(castSort)));
                AstNode<StmtExpr> n = push(new StmtExpr.AsType(v, castType));
                staticTypeOf.put(n, t);
                noteDesc(n, tc.type().asSymbol().descriptorString());
                if (heapReadValues.contains(v)) {
                    heapReadValues.add(n);
                }
                if (ctx.contractMode) {
                    return;
                }
                implicitThrows.put(n, new ImplicitThrow(
                        e(LaurelFactory.primitiveOp(new Operation.Not(), e(new StmtExpr.IsType(v, castType)))),
                        CLASS_CAST_EXCEPTION));
                return;
            }
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
            String contractSort = patternBindTests.contains(tc) ? null : ctx.contracts.wrapperSortOf(fqn);
            if (contractSort != null) {
                ctx.composites.referencedCompositeTypes.add(contractSort);
                AstNode<StmtExpr> n = push(new StmtExpr.IsType(v,
                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(contractSort)))));
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
                drop(v);
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralBool(false));
                bools.add(n);
                return;
            }
            String enclosing = enclosingInstanceFqn(origin(v));
            if (enclosing != null && ctx.resolver.isSubclassOf(enclosing, fqn)) {
                drop(v);
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
                && enclosingInstanceFqn(origin(f.target())) != null) {
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
                int under = sk.opcode() == java.lang.classfile.Opcode.DUP ? 0
                        : (sk.opcode() == java.lang.classfile.Opcode.DUP_X1 ? 1 : 2);
                arrays.refuseValueUse(under);
                duplicate(1, under, sk.opcode());
            }
            case DUP2, DUP2_X1, DUP2_X2 -> {
                int under = sk.opcode() == java.lang.classfile.Opcode.DUP2 ? 0
                        : (sk.opcode() == java.lang.classfile.Opcode.DUP2_X1 ? 1 : 2);
                arrays.refuseValueUse(under);
                duplicate(2, under, sk.opcode());
            }
            case SWAP -> {
                reorder(topSlots(2, sk.opcode()), new int[] {1, 0});
            }
            case POP -> popValue(stack.pop());
            case POP2 -> {
                AstNode<StmtExpr> v = stack.pop();
                popValue(v);
                String d = descOf.get(v);
                if (d == null) {
                    throw new JavaViolationException("Unsupported stack shape: " + sk.opcode());
                }
                if (!stack.isEmpty() && !"J".equals(d) && !"D".equals(d)) {
                    popValue(stack.pop());
                }
            }
            default -> todo(in.opcode().toString());
        }
    }

    private void popValue(AstNode<StmtExpr> v) {
        if (isStackRead(v)) {
            discard(v);
        } else if (callNodes.contains(v)) {
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

    AstNode<StmtExpr> unpush(AstNode<StmtExpr> v) {
        if (!isStackRead(v)) {
            return v;
        }
        AstNode<StmtExpr> push = pushOf.get(v.val());
        AstNode<StmtExpr> pushed = spillOrigin.get(v.val());
        if (push == null || pushed == null || stmts.isEmpty() || stmts.get(stmts.size() - 1) != push) {
            return null;
        }
        stmts.remove(stmts.size() - 1);
        return pushed;
    }

    AstNode<StmtExpr> boxedOperand(AstNode<StmtExpr> v) {
        AstNode<StmtExpr> pushed = isStackRead(v) ? spillOrigin.get(v.val()) : v;
        if (pushed == null || !boxedValue.containsKey(pushed) || unpush(v) == null) {
            return null;
        }
        return boxedValue.get(pushed);
    }

    AstNode<StmtExpr> takeCall(AstNode<StmtExpr> v) {
        AstNode<StmtExpr> boxed = boxedOperand(v);
        if (boxed != null) {
            return takeCall(boxed);
        }
        AstNode<StmtExpr> value = unpush(v);
        return value != null && value.val() instanceof StmtExpr.Call ? value : null;
    }

    void drop(AstNode<StmtExpr> v) {
        if (isStackRead(v)) {
            discard(v);
        }
    }

    void discard(AstNode<StmtExpr> read) {
        AstNode<StmtExpr> push = pushOf.get(read.val());
        if (push != null && !stmts.isEmpty() && stmts.get(stmts.size() - 1) == push
                && push.val() instanceof StmtExpr.Assign a && a.targets().size() == 1
                && a.value().val() instanceof StmtExpr.Call) {
            stmts.set(stmts.size() - 1, a.value());
            return;
        }
        addStmt(read.val());
    }

    void duplicate(int groupSlots, int underSlots, java.lang.classfile.Opcode op) {
        var group = topSlots(groupSlots, op);
        var under = topSlots(underSlots, op);
        var in = new ArrayList<>(under);
        in.addAll(group);
        int[] order = new int[2 * group.size() + under.size()];
        int t = 0;
        for (int g = 0; g < group.size(); g++) {
            order[t++] = under.size() + g;
        }
        for (int u = 0; u < under.size(); u++) {
            order[t++] = u;
        }
        for (int g = 0; g < group.size(); g++) {
            order[t++] = under.size() + g;
        }
        reorder(in, order);
    }

    private List<AstNode<StmtExpr>> topSlots(int slots, java.lang.classfile.Opcode op) {
        var taken = new ArrayList<AstNode<StmtExpr>>();
        int n = 0;
        while (n < slots && !stack.isEmpty()) {
            AstNode<StmtExpr> v = stack.pop();
            String d = descOf.get(v);
            if (d == null && slots - n >= 2) {
                throw new JavaViolationException("Unsupported stack shape: " + op);
            }
            n += "J".equals(d) || "D".equals(d) ? 2 : 1;
            taken.add(0, v);
        }
        if (n != slots) {
            throw new JavaViolationException("Unsupported stack shape: " + op);
        }
        return taken;
    }

    AstNode<StmtExpr> nameRead(AstNode<StmtExpr> read) {
        String name = mintTemp();
        addStmt(new StmtExpr.Assign(List.of(new AstNode<>(new Variable.Declare(
                new org.strata.jverify.laurel.ParameterOpt(LaurelFactory.ident(name), Optional.empty())),
                read.source())), read));
        AstNode<StmtExpr> local = e(LaurelFactory.var_(name));
        transfer(read, local);
        return local;
    }

    void emitPermute(int reads, int[] order, FileRange src) {
        var name = new StringBuilder("$permute");
        var targets = new ArrayList<AstNode<Variable>>();
        for (int r : order) {
            name.append('_').append(r);
            targets.add(new AstNode<>(new Variable.Stack(Optional.empty()), src));
        }
        var args = java.util.stream.IntStream.range(0, reads).mapToObj(r -> stackRead(src)).toList();
        addStmt(new StmtExpr.Assign(targets, e(LaurelFactory.staticCall(LaurelFactory.reservedIdent(name.toString()),
                args, List.of()))));
    }

    private void reorder(List<AstNode<StmtExpr>> in, int[] order) {
        var reads = new ArrayList<Integer>();
        for (int i = 0; i < in.size(); i++) {
            if (isStackRead(in.get(i))) {
                reads.add(i);
            }
        }
        var outReads = new ArrayList<Integer>();
        for (int o : order) {
            if (isStackRead(in.get(o))) {
                outReads.add(reads.indexOf(o));
            }
        }
        boolean identity = outReads.equals(java.util.stream.IntStream.range(0, reads.size()).boxed().toList());
        var fresh = new java.util.ArrayDeque<AstNode<StmtExpr>>();
        if (!identity) {
            FileRange src = in.get(reads.get(0)).source();
            emitPermute(reads.size(), outReads.stream().mapToInt(Integer::intValue).toArray(), src);
            for (int r : outReads) {
                AstNode<StmtExpr> source = in.get(reads.get(r));
                AstNode<StmtExpr> read = stackRead(src);
                transfer(source, read);
                AstNode<StmtExpr> origin = spillOrigin.get(source.val());
                if (origin != null) {
                    spillOrigin.put(read.val(), origin);
                }
                fresh.add(read);
            }
        }
        for (int o : order) {
            AstNode<StmtExpr> v = in.get(o);
            stack.push(isStackRead(v) && !identity ? fresh.removeFirst() : v);
        }
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
        return e(LaurelFactory.staticCall(LaurelFactory.ident(helperName), List.of(v), List.of()));
    }

    boolean isNullMarker(AstNode<StmtExpr> v) {
        return v.val() instanceof StmtExpr.LiteralString s && s.value().equals("$null");
    }

    /** The receiver's declared static type as an fqn, or null. */
    String staticTypeFqnOf(AstNode<StmtExpr> receiver) {
        receiver = origin(receiver);
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
        thrown = origin(thrown);
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

    // --- Peepholes (tryArrayInit / tryBooleanRegion) ---

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


    static boolean sameField(java.lang.classfile.instruction.FieldInstruction a,
                             java.lang.classfile.instruction.FieldInstruction b) {
        return a.owner().asInternalName().equals(b.owner().asInternalName())
                && a.name().equals(b.name());
    }

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
        boolean wasSpilling = spilling;
        spilling = false;
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
            spilling = wasSpilling;
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

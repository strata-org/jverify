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
 * message texts — matches JFE's observable behavior (the corpus asserts
 * byte-identical diagnostics), applied at the corresponding instruction.
 * Invocations, JVerify spec calls, and field accesses are delegated to
 * {@link InvokeTranslator}, {@link SpecCallTranslator}, and
 * {@link FieldAccessTranslator}, which share this body's stack and side
 * tables.
 *
 * <p><b>Range discipline.</b> Every sub-expression of a statement carries
 * the statement's narrowed source range. Expressions are built bottom-up
 * before their owning statement is known, so nodes carry UNKNOWN_RANGE until
 * {@link #addStmt}, which computes the statement's range (CRT span narrowed
 * per {@link Narrow}) and restamps the subtree via
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

    // --- Variable bookkeeping ---
    final Set<String> declared;              // variable keys already declared (slot@scopeStart, or v<slot>)
    final Map<Integer, String> slotName, slotDesc;
    Locals locals;                // scoped local table (nullable); resolved at `pos`
    int pos = -1;                            // current seq-index, for scoped local resolution
    ClassModel cm;                           // enclosing class, for resolving lambda impl methods
    List<CrtRange> crt = List.of();
    int[] seqToBci;
    boolean inCtor;
    boolean dropSuper;
    String retDesc;
    final Map<Integer, String> slotAlias = new HashMap<>();  // slot -> minted name for an unnamed local
    final Map<Integer, String> slotType = new HashMap<>();   // slot -> descriptor it currently holds
    int synthCounter;
    int pendingIncSlot = -1;                 // deferred mid-expression iinc (prefix ++x)
    int pendingIncDelta;
    Map<Integer, List<java.lang.classfile.Annotation>> slotTypeAnnos = Map.of();

    // --- Laurel-side side tables (identity-keyed on the nodes this layer built) ---
    /** Pushed value's JVM descriptor where known (boolean/int erasure oracle). */
    final Map<AstNode<StmtExpr>, String> descOf = new IdentityHashMap<>();
    /** Values known to be boolean-typed. */
    final Set<AstNode<StmtExpr>> bools = Collections.newSetFromMap(new IdentityHashMap<>());
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
     * packages, matching JFE's simple-name resolution.
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
     * specially by spec calls and `return`; anywhere else refuses with
     * JFE's JCLambda message.
     */
    final Map<AstNode<StmtExpr>, LambdaHandle> lambdas = new IdentityHashMap<>();

    /** An invokedynamic-produced closure: the synthetic impl (lambda) or referenced method. */
    record LambdaHandle(String implName, String implDesc, int captureCount, boolean isMethodRef) {
    }

    /**
     * Lambdas hidden behind a functional-interface CHECKCAST: the marker
     * refuses as JCLambda where a bare lambda is required, but throwsOn case
     * postconditions unwrap the cast (as JFE unwraps parens and casts).
     */
    final Map<AstNode<StmtExpr>, LambdaHandle> castWrappedLambdas = new IdentityHashMap<>();

    // --- The per-concern translators sharing this body's stack and side tables ---
    final InvokeTranslator invokes;
    final SpecCallTranslator specCalls;
    final FieldAccessTranslator fieldAccess;

    BodyTranslator(TranslationContext ctx, Map<Integer, String> slotName, Map<Integer, String> slotDesc,
               Set<String> declared) {
        this.ctx = ctx;
        this.slotName = slotName;
        this.slotDesc = slotDesc;
        this.declared = new java.util.HashSet<>(declared);
        this.invokes = new InvokeTranslator(this);
        this.specCalls = new SpecCallTranslator(this);
        this.fieldAccess = new FieldAccessTranslator(this);
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

    /** How a statement's raw CRT span narrows to its stamped source range. */
    enum Narrow {
        /** Raw CRT span (non-ExpressionStmt statements: return, throw, ...). */
        RAW,
        /** Trailing ';' and whitespace stripped (ExpressionStmt that is not a declaration). */
        STRIP_SEMI,
        /** Declarations keep the semicolon (ExpressionStmt(VariableDeclarationExpr)). */
        DECL
    }

    /**
     * The statement's FileRange: the CRT span of the instruction at
     * {@code seqIdx}, mapped to source offsets with the {@link Narrow}ing
     * applied. Returns the context's current default range when no span is
     * known.
     */
    FileRange stmtRange(int seqIdx, Narrow narrow, boolean containsLambda) {
        Span span = spanAt(seqIdx);
        if (span.isNone() || ctx.src.index == null) {
            return ctx.src.defaultRange;
        }
        int start = ctx.src.index.offset(span.beginLine(), span.beginCol());
        int end = ctx.src.index.offset(span.endLine(), span.endCol());
        // A statement that materializes a lambda carries the invokedynamic's
        // CRT window, which starts at the class declaration rather than the
        // statement (keyed on the statement containing a lambda).
        if (span.beginLine() != span.endLine() && containsLambda) {
            int lineStart = ctx.src.index.offset(span.endLine(), 1);
            int firstNonWs = lineStart;
            while (firstNonWs < end && Character.isWhitespace(ctx.src.source.charAt(firstNonWs))) {
                firstNonWs++;
            }
            start = firstNonWs;
        }
        if (narrow == Narrow.STRIP_SEMI) {
            while (end > start && (ctx.src.source.charAt(end - 1) == ';'
                    || Character.isWhitespace(ctx.src.source.charAt(end - 1)))) {
                end--;
            }
        }
        return ctx.src.rangeOf(start, end);
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
        // refusal wins, matching JFE's bottom-up conversion order.
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
    }

    /** The per-node refusal checks shared by the placement/contract walks. */
    private void refuseNode(AstNode<StmtExpr> n) {
        String needle = arrayInitNeedles.get(n);
        if (needle != null) {
            // Array creation with initializers: reported at the first
            // element's text, then aborted with ReportedViolation.
            ctx.reporter.reportError(ctx.src.uri, ctx.src.pointRangeAt(needle),
                    "new array with initializers is not supported");
            throw new BytecodeToLaurelCompiler.ReportedViolation();
        }
        String refusal = deferredRefusal.get(n);
        if (refusal != null) {
            throw new JavaViolationException(refusal);
        }
        if (lambdas.containsKey(n)) {
            // JFE's convertExpression default-arm message on a lambda/method reference.
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
            case StmtExpr.Block n -> out.addAll(n.statements());
            case StmtExpr.While n -> {
                out.add(n.cond());
                out.addAll(n.invariants());
                n.decreases().ifPresent(out::add);
                out.add(n.body());
            }
            case StmtExpr.Return n -> n.value().ifPresent(out::add);
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

    /** Emit a statement stamped with the current instruction's narrowed range. */
    void addStmt(StmtExpr stmt, Narrow narrow) {
        addStmtAt(stmt, pos, narrow, false);
    }

    void addStmtAt(StmtExpr stmt, int seqIdx, Narrow narrow, boolean containsLambda) {
        AstNode<StmtExpr> raw = e(stmt);
        FileRange range = stmtRange(seqIdx, narrow, containsLambda);
        // The per-statement default range: refusal reports inside this
        // statement (pointRangeAt needles) anchor within it.
        ctx.src.defaultRange = range;
        checkPlaceable(raw);
        AstNode<StmtExpr> placed = place(raw, range);
        if (narrow == Narrow.STRIP_SEMI) {
            stripSemiAt.put(placed, seqIdx);
        }
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
        var oc = childrenOf(oldN.val());
        var nc = childrenOf(newN.val());
        for (int i = 0; i < Math.min(oc.size(), nc.size()); i++) {
            remapIdentity(oc.get(i), nc.get(i));
        }
    }

    /**
     * Statements placed with the semicolon-stripped narrowing -> their seq
     * index: loop-body statements are re-stamped with the FULL statement
     * range (semicolon included), which only differs for these.
     */
    final Map<AstNode<StmtExpr>, Integer> stripSemiAt = new IdentityHashMap<>();

    // --- Locals: slot naming and typing ---

    String localName(int slot) {
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) {
                return s.name();
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
                if (s.genericType() != null) {
                    return TypeRef.ofRendered(s.genericType(), isNat, isUnbounded);
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
        return e(LaurelFactory.var_(ctx.method.renames.getOrDefault(name, name)));
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

    // --- Coercions (fused Body.coerce/coerceTo/coerceArg + convert-time forms) ---

    /** boolean target: 0/1 literals become false/true (old coerce(v,"Z")). */
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
     * NullLiteralExpr" (JFE's message) unless a coercion consumes it (a cast
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

    /** LOAD: push the named local (fused NameExpr conversion: renames + this -> self?). */
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
        stack.push(n);
        noteDesc(n, localDesc(li.slot()));
        TypeRef t = localTypeRef(li.slot());
        if (t != null && !name.equals("this")) {
            staticTypeOf.put(n, t);
        }
    }

    private AstNode<Variable> localVarTarget(int slot) {
        String name = localName(slot);
        String laurelName = name.equals("this")
                ? LaurelFactory.LAUREL_SELF_BINDING
                : ctx.method.renames.getOrDefault(name, name);
        return new AstNode<>(new Variable.Local(LaurelFactory.ident(laurelName)), SourceMap.UNKNOWN_RANGE);
    }

    /** STORE: declaration-or-assignment decision ladder. */
    void store(StoreInstruction si) {
        String desc = localDesc(si.slot());
        AstNode<StmtExpr> val = coerceTo(stack.pop(), desc);
        // Capturing the ThrowsOnCase handle in a local: the declaration ends
        // the contract block: the declaration's TYPE registers its composite
        // sort (a side effect the emitted program keeps), then the throwsOn
        // initializer refuses.
        SpecStmt spec = specStmts.get(val);
        if (spec != null && spec.name().equals("throwsOn")) {
            if (!declared.contains(varKey(si.slot()))) {
                TypeRef t = localTypeRef(si.slot());
                if (t != null) {
                    t.toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
                }
            }
            throw new JavaViolationException("Unsupported JVerify method: throwsOn");
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
            // Declaration (old: VariableDeclarationExpr -> convertVariableDeclaration).
            TypeRef t = localTypeRef(si.slot());
            ParameterOpt param = t == null
                    ? new ParameterOpt(LaurelFactory.ident(localName(si.slot())), Optional.empty())
                    : new ParameterOpt(LaurelFactory.ident(localName(si.slot())),
                            Optional.of(LaurelFactory.node(
                                    t.toLaurel(ctx.typeParams, ctx.resolver, ctx.composites))));
            var declare = new AstNode<Variable>(new Variable.Declare(param), SourceMap.UNKNOWN_RANGE);
            addStmt(new StmtExpr.Assign(List.of(declare), val), Narrow.DECL);
        } else {
            addStmt(new StmtExpr.Assign(List.of(localVarTarget(si.slot())), val), Narrow.STRIP_SEMI);
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
                localVarTarget(ii.slot()), e(new StmtExpr.LiteralInt(Math.abs(c)))), Narrow.STRIP_SEMI);
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
                localVarTarget(slot), e(new StmtExpr.LiteralInt(Math.abs(c)))), Narrow.STRIP_SEMI);
    }

    /** Replace an unconsumed stacked read of {@code nm} with a postfix inc/dec. */
    boolean replaceStackedRead(String nm, boolean inc) {
        if (stack.isEmpty()) {
            return false;
        }
        AstNode<StmtExpr> top = stack.peek();
        String laurelName = nm.equals("this")
                ? LaurelFactory.LAUREL_SELF_BINDING : ctx.method.renames.getOrDefault(nm, nm);
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
     * same slot by one while a value is still live on the stack (old
     * asIncrementExpr / asIncrementExprOn for the local case).
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

    // --- Operators (fused Body.operator + convertBinary/convertUnary) ---

    /** Binary $op push. */
    void bin(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        push(LaurelFactory.primitiveOp(op, a, b));
    }

    /** A bitwise/shift operator: defer with JFE's refusal message. */
    void binRefused(String opName) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        AstNode<StmtExpr> n = push(LaurelFactory.primitiveOp(new Operation.Add(), a, b));
        defer(n, "Unsupported binary op: " + opName);
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
                LaurelFactory.ident("$cmp3$" + boxType), List.of(a, b)));
        cmp3.put(n, pair);
        if (nanHigh != null) {
            cmp3NanHigh.put(n, nanHigh);
        }
        // Unconsumed, a bare 3-way compare surfaces as a Long.compare-style
        // call to an unknown class and refuses (JFE's behavior).
        defer(n, "Call to unknown class '" + boxType + "' is not supported");
    }

    // --- Boolean materialization (fused materializeComparison/cmp/cmpLit/unaryBool) ---

    /** The materialized boolean is the NEGATION of the branch condition. */
    AstNode<StmtExpr> materializeComparison(java.lang.classfile.Opcode op) {
        return switch (op) {
            case IF_ICMPEQ, IF_ACMPEQ -> cmp(new Operation.Neq());
            case IF_ICMPNE, IF_ACMPNE -> cmp(new Operation.Eq());
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

    private AstNode<StmtExpr> cmp(Operation op) {
        AstNode<StmtExpr> b = stack.pop(), a = stack.pop();
        if ((op instanceof Operation.Eq || op instanceof Operation.Neq) && (isBool(a) || isBool(b))) {
            a = coerce(a, "Z");
            b = coerce(b, "Z");
        }
        return e(LaurelFactory.primitiveOp(op, a, b));
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
     * pureMethods map: the heap-write fallback (bodyPotentiallyWritesHeap)
     * walks the emitted Laurel tree and answers call purity through this.
     */
    final Map<StmtExpr, String> calleeKeys = new IdentityHashMap<>();

    /**
     * Spec-call statements (precondition/postcondition/.../throwsOn chains)
     * emitted as marker statements: the driver's contract extraction reads
     * this table. A marker in body position past the header refuses with the
     * old "call to contract method must come before the rest of the body".
     */
    final Map<AstNode<StmtExpr>, SpecStmt> specStmts = new IdentityHashMap<>();

    /** One recognized spec-call statement (or a throwsOn(...).x(...).y(...) chain). */
    record SpecStmt(String name, List<AstNode<StmtExpr>> args, BodyTranslator.LambdaHandle lambdaArg,
                    List<SpecLink> links, int seqIdx) {
    }

    /** One chained clause of a throwsOn case. */
    record SpecLink(String name, AstNode<StmtExpr> arg, BodyTranslator.LambdaHandle lambdaArg) {
    }

    /** Emitted Throw statements -> the thrown static type fqn (or null): driver prescan parity. */
    final Map<AstNode<StmtExpr>, String> throwStmts = new IdentityHashMap<>();

    /**
     * Structuring gaps (old {@code todo()} EmptyStmt comments). The old
     * translateMethod scanned these FIRST and refused the whole method with
     * the comment text, before any conversion refusal could fire; collected
     * here and thrown by the driver ahead of other refusals.
     */
    final List<String> todos = new ArrayList<>();

    void todo(String msg) {
        todos.add("TODO unsupported: " + msg);
    }

    // --- Dispatch (ports of Body.run / translateInstrs / translateSpan) ---

    static int seqAt(List<Integer> idxs, int p) {
        return idxs == null ? p : idxs.get(p);
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

    /** MAIN opcode switch (Body.translate fused with the conversion layer). */
    void translate(Instruction in) {
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
                    // A returned lambda/method reference: allocate the
                    // return sort directly.
                    if (lambdas.containsKey(v) && ctx.method.returnSort != null) {
                        ctx.composites.referencedCompositeTypes.add(ctx.method.returnSort);
                        addStmt(new StmtExpr.Return(Optional.of(e(new StmtExpr.New(
                                LaurelFactory.ident(ctx.method.returnSort), List.of())))), Narrow.RAW);
                    } else {
                        addStmt(new StmtExpr.Return(Optional.of(coerce(v, retDesc))), Narrow.RAW);
                    }
                }
                // A bare `return` in the middle of a method becomes the Laurel
                // short form only via the structurer; void fall-off emits nothing.
            }
            default -> {
                if (in.opcode() == java.lang.classfile.Opcode.ATHROW) {
                    AstNode<StmtExpr> v = stack.pop();
                    AstNode<StmtExpr> before = e(new StmtExpr.Throw(v));
                    checkPlaceable(before);
                    // Coverage discipline first, then the hierarchy chain
                    // link for the `is` guard.
                    String thrownFqn = thrownStaticTypeFqn(v);
                    checkThrowCoverage(thrownFqn, v);
                    ctx.composites.registerHierarchyUpToThrowable(thrownFqn);
                    FileRange range = stmtRange(pos, Narrow.RAW, false);
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
            case IDIV, LDIV, FDIV, DDIV -> bin(new Operation.DivT());
            case IREM, LREM, FREM, DREM -> bin(new Operation.ModT());
            case IAND, LAND -> binRefused("BINARY_AND");
            case IOR, LOR -> binRefused("BINARY_OR");
            case IXOR, LXOR -> binRefused("XOR");
            case ISHL, LSHL -> binRefused("LEFT_SHIFT");
            case ISHR, LSHR -> binRefused("SIGNED_RIGHT_SHIFT");
            case IUSHR, LUSHR -> binRefused("UNSIGNED_RIGHT_SHIFT");
            case INEG, LNEG, FNEG, DNEG -> neg();
            case LCMP -> push3Way("Long", null);
            case FCMPL -> push3Way("Float", Boolean.FALSE);
            case FCMPG -> push3Way("Float", Boolean.TRUE);
            case DCMPL -> push3Way("Double", Boolean.FALSE);
            case DCMPG -> push3Way("Double", Boolean.TRUE);
            case ARRAYLENGTH -> {
                stack.pop();
                // FieldAccessExpr "length": the conversion's field machinery
                // never resolves it -> "Unsupported field access: x.length".
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
            // float/double conversions: JFE's fully-qualified cast refusal.
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
        String sourceName = LaurelFactory.laurelTypeName(sourceType);
        String helperName = "$cast_to_" + laurelTarget + "_from_" + sourceName;
        ctx.lf.registerCastHelper(helperName, sourceName, sourceType, laurelTarget, targetType);
        AstNode<StmtExpr> n = push(new StmtExpr.StaticCall(
                LaurelFactory.ident(helperName), List.of(operand)));
        noteDesc(n, ClassfileQueries.convDesc(cv.opcode()));
    }

    /**
     * The cast operand's static type without javac attribution (old
     * inferCastSourceType): a local's declared type when known, a stacked
     * cast's target, int64 for long literals, else int32.
     */
    private HighType inferCastSourceType(AstNode<StmtExpr> operand) {
        TypeRef declared = staticTypeOf.get(operand);
        if (declared != null) {
            try {
                return declared.toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
            } catch (JavaViolationException ignored) {
                // fall through to the defaults
            }
        }
        if (operand.val() instanceof StmtExpr.StaticCall call
                && call.callee().text().startsWith("$cast_to_")) {
            String t = call.callee().text().substring("$cast_to_".length());
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

    /** CHECKCAST / INSTANCEOF fused with convertTypeCast / InstanceOfExpr conversion. */
    void typeCheck(java.lang.classfile.instruction.TypeCheckInstruction tc) {
        TypeRef t = TypeRef.ofClassDesc(tc.type().asSymbol());
        AstNode<StmtExpr> v = stack.pop();
        String fqn = resolveFqn(t);
        if (tc.opcode() == java.lang.classfile.Opcode.CHECKCAST) {
            if (fqn != null && ctx.resolver.isThrowableSubtype(fqn)) {
                ctx.composites.registerHierarchyUpToThrowable(fqn);
                AstNode<StmtExpr> n = push(new StmtExpr.AsType(v,
                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn)))));
                staticTypeOf.put(n, t);
                return;
            }
            // A functional-interface cast over a lambda/method reference: the
            // old CastExpr conversion converted the INNER expression first and
            // refused on it as JCLambda. throwsOn case postconditions unwrap
            // such casts (convertCasePostcondition's skipParens+Cast loop).
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
            if (fqn == null || !ctx.resolver.isThrowableSubtype(fqn)) {
                AstNode<StmtExpr> n = push(new StmtExpr.LiteralString("$instanceof"));
                bools.add(n);
                defer(n, "instanceof on opaque reference types is not yet supported");
                return;
            }
            ctx.composites.registerHierarchyUpToThrowable(fqn);
            AstNode<StmtExpr> n = push(new StmtExpr.IsType(v,
                    LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn)))));
            bools.add(n);
        }
    }

    /** Resolve a class TypeRef to an fqn, matching JFE's resolution (see parity notes). */
    String resolveFqn(TypeRef t) {
        if (t.kind() != TypeRef.Kind.CLASS) {
            return null;
        }
        String qualified = ctx.resolver.qualifyTypeName(t.nameWithScope());
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
                insertDup(top, depth);
            }
            case DUP2, DUP2_X1, DUP2_X2 -> {
                AstNode<StmtExpr> top = stack.peek();
                int depth = sk.opcode() == java.lang.classfile.Opcode.DUP2 ? 0
                        : (sk.opcode() == java.lang.classfile.Opcode.DUP2_X1 ? 1 : 2);
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
                    addStmt(v.val(), Narrow.STRIP_SEMI);
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
                                stmtRange(pos, Narrow.STRIP_SEMI, hasLambda));
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
     * Coerce a call ARGUMENT to its parameter type (old coerceArg): boolean
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
        String helperName = "$cast_to_" + narrow + "_from_" + sourceName;
        ctx.lf.registerCastHelper(helperName, sourceName, sourceType, narrow, targetType);
        return e(new StmtExpr.StaticCall(LaurelFactory.ident(helperName), List.of(v)));
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
        return ctx.resolver.qualifyTypeName(t.nameWithScope());
    }

    /** The thrown expression's static type fqn (old thrownStaticTypeFqn), or null. */
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
     * Throw-coverage check over a Laurel-thrown value: a throw needs an
     * enclosing catch arm that covers its type or the declared exceptional
     * exit (JFE's declared-throws discipline).
     */
    void checkThrowCoverage(String thrownFqn, AstNode<StmtExpr> thrown) {
        if (thrownFqn == null || !ctx.resolver.isThrowableSubtype(thrownFqn)) {
            throw new JavaViolationException(
                    "Unsupported thrown expression type: " + displayOf(thrown));
        }
        boolean caughtLocally = ctx.method.enclosingCatchTypes.stream().anyMatch(arms ->
                arms.stream().anyMatch(c -> ctx.resolver.isSubclassOf(thrownFqn, c)));
        if (caughtLocally) {
            return;
        }
        if (ctx.method.throwsJoin == null) {
            throw new JavaViolationException(
                    "throw requires the enclosing method to declare the thrown type"
                            + " in its throws clause");
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

    // --- Fields (fused Body.field + FieldAccessExpr conversion) ---

    // --- invokedynamic (LambdaMetafactory / StringConcatFactory) ---

    // --- Peepholes (ports of tryArrayInit / tryCompoundAssign / tryBooleanRegion) ---

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
        if (k + 1 >= seq.size() || !isDup(seq.get(k + 1)) || stack.isEmpty()) {
            return 0;
        }
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
        // Old shape: ArrayCreationExpr with initializer. The conversion
        // reported at the FIRST element's text and threw ReportedViolation.
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
                    coerce(rhs, gf.typeSymbol().descriptorString())), Narrow.STRIP_SEMI);
            return (j + 1) - k;
        }
        return 0;
    }

    /** Compound-assignment operator support, refusing the rest with JFE's operator names. */
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

    /** Boolean materialization region (old tryBooleanRegion; && folds to $andThen). */
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
        for (int j = k; j < p; j++) {
            CodeElement el = seq.get(j);
            if (el instanceof java.lang.classfile.instruction.BranchInstruction b
                    && ClassfileQueries.isConditionalBranch(b.opcode())) {
                guards.add(materializeComparison(b.opcode()));
            } else {
                translate((Instruction) el);
            }
        }
        if (guards.isEmpty()) {
            return 0;
        }
        AstNode<StmtExpr> result = guards.get(0);
        for (int i = 1; i < guards.size(); i++) {
            // Old: BinaryExpr.AND -> convertBinary -> AndThen.
            result = e(LaurelFactory.primitiveOp(new Operation.AndThen(), result, guards.get(i)));
        }
        bools.add(result);
        stack.push(result);
        return (p + 5) - k;
    }

}

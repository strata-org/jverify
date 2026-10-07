package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.QuantifierMode;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translates one-dimensional primitive array instructions through the
 * {@link ArrayModel}: {@code newarray} as the inline creation contract,
 * {@code xaload}/{@code xastore}/{@code arraylength} as calls of the JArray
 * members (or of the generic wrappers on a receiver that is not a local),
 * with the element range helpers around loads and stores. Only allocations
 * and writes emit statements, and only where they run exactly once, in
 * Java's order ({@link #checkPlacement}). Element reads refuse in a
 * pre-tested loop condition, which Strata evaluates once before the loop
 * when it calls a procedure. Array constructs outside the model
 * (reference, multi-dimensional and float/double arrays, arrays used as
 * Objects, array constructor references, code compiled without {@code -g})
 * refuse the method.
 *
 * <p>Every entry point is reached only while the model is enabled; an
 * instruction whose operand is a {@code $}-marker keeps the body's original
 * arm ({@code handles*}).
 */
final class ArrayAccessTranslator {

    private final BodyTranslator b;
    private final TranslationContext ctx;

    ArrayAccessTranslator(BodyTranslator b) {
        this.b = b;
        this.ctx = b.ctx;
    }

    boolean enabled() {
        return ctx.composites.arrays.enabled();
    }

    // --- Dispatch gates ---

    boolean handlesNewArray() {
        return enabled() && !b.stack.isEmpty() && !isMarker(b.stack.peek());
    }

    boolean handlesLoad(ArrayLoadInstruction al) {
        return enabled() && operandsAreNotMarkers(2);
    }

    boolean handlesStore(ArrayStoreInstruction as) {
        return enabled() && operandsAreNotMarkers(3);
    }

    boolean handlesLength() {
        return enabled() && !b.stack.isEmpty() && !isMarker(b.stack.peek());
    }

    private boolean operandsAreNotMarkers(int count) {
        if (b.stack.size() < count) {
            return false;
        }
        var it = b.stack.iterator();
        for (int i = 0; i < count; i++) {
            if (isMarker(it.next())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMarker(AstNode<StmtExpr> n) {
        return n.val() instanceof StmtExpr.LiteralString;
    }

    /** The element kinds an opcode accepts ({@code baload}/{@code bastore}: byte or boolean), or null. */
    private static List<ArrayModel.ElementKind> expectedKinds(Opcode op) {
        return switch (op) {
            case IALOAD, IASTORE -> List.of(ArrayModel.ElementKind.INT32);
            case LALOAD, LASTORE -> List.of(ArrayModel.ElementKind.INT64);
            case SALOAD, SASTORE -> List.of(ArrayModel.ElementKind.INT16);
            case CALOAD, CASTORE -> List.of(ArrayModel.ElementKind.CHAR);
            case BALOAD, BASTORE -> List.of(ArrayModel.ElementKind.INT8, ArrayModel.ElementKind.BOOL);
            default -> null;
        };
    }

    // --- newarray ---

    /**
     * {@code newarray}: the creation contract inline, then the new array on
     * the stack as the local {@code t$A}.
     * <pre>
     * var t$K: int32 := n                  (unless n is a literal or a bounded local)
     * assert n &gt;= 0 summary "non-negative array size"   (unless a literal n &gt;= 0)
     * var t$A: JArray&lt;E&gt; := new JArray&lt;E&gt;
     * assume t$A#length() == n
     * assume forall(t$B: int32) =&gt; 0 &lt;= t$B &amp;&amp; t$B &lt; n ==&gt; t$A#get(t$B) == zero
     * </pre>
     */
    void newPrimitiveArray(NewPrimitiveArrayInstruction na) {
        ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        ArrayModel.ElementKind k = ArrayModel.ofTypeKind(na.typeKind());
        AstNode<StmtExpr> n = b.stack.pop();
        checkPlacement();
        ContractMember length = ctx.composites.arrays.member("length");
        ContractMember get = ctx.composites.arrays.member("get");
        HighType arrayType = ctx.composites.arrays.jarrayOf(LaurelFactory.node(k.carrier()));

        StmtExpr count = n.val();
        if (!(n.val() instanceof StmtExpr.LiteralInt) && !isBoundedLocal(n)) {
            // The count is evaluated once, here, and must be a Java int.
            String c = b.mintTemp();
            b.addStmt(declare(c, new HighType.UserDefined(LaurelFactory.ident("int32")), n));
            count = local(c);
        }
        if (!(count instanceof StmtExpr.LiteralInt lit && lit.value() >= 0)) {
            b.addStmt(new StmtExpr.Assert(b.e(LaurelFactory.primitiveOp(new Operation.Geq(),
                    b.e(count), b.e(new StmtExpr.LiteralInt(0)))), Optional.of("non-negative array size")));
        }
        String a = b.mintTemp();
        b.allocTemps.add(a);
        b.addStmt(declare(a, arrayType, b.e(new StmtExpr.New(LaurelFactory.ident(ArrayModel.SORT),
                List.of(LaurelFactory.node(k.carrier()))))));
        AstNode<StmtExpr> lengthCall = b.e(new StmtExpr.InstanceCall(b.e(local(a)),
                LaurelFactory.ident("length"), List.of()));
        record(length, lengthCall, "length");
        b.addStmt(new StmtExpr.Assume(b.e(LaurelFactory.primitiveOp(new Operation.Eq(), lengthCall, b.e(count)))));
        String i = b.mintTemp();
        AstNode<StmtExpr> inRange = b.e(LaurelFactory.primitiveOp(new Operation.And(),
                b.e(LaurelFactory.primitiveOp(new Operation.Leq(), b.e(new StmtExpr.LiteralInt(0)), b.e(local(i)))),
                b.e(LaurelFactory.primitiveOp(new Operation.Lt(), b.e(local(i)), b.e(count)))));
        AstNode<StmtExpr> getCall = b.e(new StmtExpr.InstanceCall(b.e(local(a)),
                LaurelFactory.ident("get"), List.of(b.e(local(i)))));
        record(get, getCall, "get");
        AstNode<StmtExpr> isZero = b.e(LaurelFactory.primitiveOp(new Operation.Eq(), getCall, b.e(k.zero())));
        b.addStmt(new StmtExpr.Assume(b.e(new StmtExpr.Quantifier(new QuantifierMode.Forall(),
                new org.strata.jverify.laurel.Parameter(LaurelFactory.ident(i),
                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident("int32")))),
                Optional.empty(), b.e(LaurelFactory.primitiveOp(new Operation.Implies(), inRange, isZero))))));
        AstNode<StmtExpr> result = b.push(local(a));
        b.noteDesc(result, k.descriptor());
        b.staticTypeOf.put(result, TypeRef.ofDescriptor(k.descriptor()));
    }

    /** A local whose declared Laurel type is a bounded primitive (so it needs no int32 binding). */
    private boolean isBoundedLocal(AstNode<StmtExpr> n) {
        TypeRef t = b.staticTypeOf.get(n);
        return isLocal(n) && t != null && t.kind() == TypeRef.Kind.PRIMITIVE && !t.isUnbounded();
    }

    // --- Loads, stores, length ---

    /**
     * {@code xaload}: {@code java?array_load_<k>(a#get(i))} on a local
     * receiver, {@code java?array_load_<k>(java?array_get<E>(a, i))}
     * otherwise; a boolean element is the raw read.
     */
    void load(ArrayLoadInstruction al) {
        AstNode<StmtExpr> index = b.stack.pop();
        AstNode<StmtExpr> arr = b.stack.pop();
        refuseUnmodelled(al.opcode(), arr);
        ArrayModel.ElementKind k = receiverKind(arr, expectedKinds(al.opcode()));
        if (b.inLoopCondition()) {
            // Strata runs the read (a call with a precondition, or an opaque
            // helper) once, before the loop, so the guard would never change.
            throw new JavaViolationException(ArrayModel.LOOP_CONDITION_READ);
        }
        ContractMember get = ctx.composites.arrays.member("get");
        AstNode<StmtExpr> read = elementRead(arr, index, k, get);
        AstNode<StmtExpr> value;
        if (k == ArrayModel.ElementKind.BOOL) {
            value = read;
            b.bools.add(value);
        } else {
            ctx.lf.registerArrayElementHelpers(k);
            value = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(ArrayModel.LOAD_HELPER_PREFIX + k.helperSuffix()),
                    List.of(read), List.of()));
        }
        b.stack.push(value);
        b.noteDesc(value, k.elementDescriptor());
    }

    private AstNode<StmtExpr> elementRead(AstNode<StmtExpr> arr, AstNode<StmtExpr> index,
                                          ArrayModel.ElementKind k, ContractMember get) {
        AstNode<StmtExpr> call;
        if (isLocal(arr)) {
            call = b.e(new StmtExpr.InstanceCall(arr, LaurelFactory.ident("get"), List.of(index)));
        } else {
            ctx.lf.registerArrayWrappers();
            call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(ArrayModel.GET_WRAPPER), List.of(arr, index),
                    List.of(LaurelFactory.node(k.carrier()))));
        }
        record(get, call, "get");
        return call;
    }

    /**
     * {@code xastore}: the statement {@code a#set(i, java?array_store_<k>(v))}
     * ({@code coerce(v, "Z")} for a boolean element). A receiver that is not
     * a local is first bound to a minted local, {@code var t$W: JArray<E> := r}.
     */
    void store(ArrayStoreInstruction as) {
        AstNode<StmtExpr> value = b.stack.pop();
        AstNode<StmtExpr> index = b.stack.pop();
        AstNode<StmtExpr> arr = b.stack.pop();
        refuseUnmodelled(as.opcode(), arr);
        ArrayModel.ElementKind k = receiverKind(arr, expectedKinds(as.opcode()));
        checkPlacement();
        ContractMember set = ctx.composites.arrays.member("set");
        AstNode<StmtExpr> stored;
        if (k == ArrayModel.ElementKind.BOOL) {
            stored = b.coerce(value, "Z");
            if (!b.isBool(stored)) {
                throw new JavaViolationException(ArrayModel.NON_BOOLEAN_STORE);
            }
        } else {
            ctx.lf.registerArrayElementHelpers(k);
            stored = b.e(new StmtExpr.StaticCall(
                    LaurelFactory.ident(ArrayModel.STORE_HELPER_PREFIX + k.helperSuffix()), List.of(value), List.of()));
        }
        AstNode<StmtExpr> receiver = arr;
        if (!isLocal(arr)) {
            // Strata rejects an instance call on a field read or call result;
            // Java evaluates the array reference first, as the binding does.
            String w = b.mintTemp();
            b.addStmt(declare(w, ctx.composites.arrays.jarrayOf(LaurelFactory.node(k.carrier())), arr));
            receiver = b.e(local(w));
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(receiver, LaurelFactory.ident("set"),
                List.of(index, stored)));
        record(set, call, "set");
        b.callNodes.add(call);
        b.addStmt(call.val());
    }

    /** {@code arraylength}: {@code a#length()} on a local receiver, {@code java?array_length<E>(a)} otherwise. */
    void length() {
        ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        AstNode<StmtExpr> arr = b.stack.pop();
        ArrayModel.ElementKind k = receiverKind(arr, null);
        ContractMember length = ctx.composites.arrays.member("length");
        AstNode<StmtExpr> call;
        if (isLocal(arr)) {
            call = b.e(new StmtExpr.InstanceCall(arr, LaurelFactory.ident("length"), List.of()));
        } else {
            ctx.lf.registerArrayWrappers();
            call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(ArrayModel.LENGTH_WRAPPER), List.of(arr),
                    List.of(LaurelFactory.node(k.carrier()))));
        }
        record(length, call, "length");
        b.stack.push(call);
        b.noteDesc(call, "I");
    }

    /**
     * Before an element load or store: refuse code compiled without
     * {@code -g}, and the reference, float and double element opcodes.
     */
    private void refuseUnmodelled(Opcode op, AstNode<StmtExpr> arr) {
        ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        String reason = ArrayModel.unmodelledAccess(op, b.descOf.get(arr));
        if (reason != null) {
            throw new JavaViolationException(reason);
        }
    }

    // --- Out-of-scope constructs (refused per method) ---

    /** {@code anewarray}: an array of arrays refuses {@link ArrayModel#MULTI}, any other {@link ArrayModel#REFERENCE}. */
    void newReferenceArray(NewReferenceArrayInstruction na) {
        ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        throw new JavaViolationException(na.componentType().asSymbol().isArray()
                ? ArrayModel.MULTI : ArrayModel.REFERENCE);
    }

    /** {@code multianewarray}: refuses {@link ArrayModel#MULTI}. */
    void newMultiArray() {
        ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        throw new JavaViolationException(ArrayModel.MULTI);
    }

    /**
     * Refuse an array value used where its static type is not exactly its
     * array type: {@code target} is the descriptor of the parameter, local,
     * field or return it flows into, or null where any array is an Object
     * use (a receiver, {@code jequals}, a string concatenation operand).
     *
     * @throws JavaViolationException {@link ArrayModel#AS_OBJECT}
     */
    void refuseAsObject(AstNode<StmtExpr> value, String target) {
        if (!enabled()) {
            return;
        }
        String d = b.descOf.get(value);
        if (d != null && d.startsWith("[") && !d.equals(target)) {
            throw new JavaViolationException(ArrayModel.AS_OBJECT);
        }
    }

    /**
     * True iff {@code handle} is an array constructor reference: javac's
     * synthetic static impl whose code only passes its {@code int}
     * parameters to one array allocation and returns the array (the shape of
     * {@code int[]::new}, and of the equivalent {@code n -> new int[n]}).
     */
    boolean isArrayConstructorRef(BodyTranslator.LambdaHandle handle) {
        if (!enabled() || handle == null || !handle.implDesc().contains(")[")) {
            return false;
        }
        var impl = b.specCalls.findImplMethod(handle);
        if (impl == null || !impl.flags().has(java.lang.reflect.AccessFlag.STATIC)) {
            return false;
        }
        var code = impl.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return false;
        }
        var ins = new ArrayList<java.lang.classfile.Instruction>();
        for (var el : code) {
            if (el instanceof java.lang.classfile.Instruction i) {
                ins.add(i);
            }
        }
        int params = impl.methodTypeSymbol().parameterCount();
        if (ins.size() != params + 2) {
            return false;
        }
        for (int i = 0; i < params; i++) {
            if (!(ins.get(i) instanceof java.lang.classfile.instruction.LoadInstruction li) || li.slot() != i) {
                return false;
            }
        }
        var alloc = ins.get(params);
        return (alloc instanceof NewPrimitiveArrayInstruction
                || alloc instanceof NewReferenceArrayInstruction
                || alloc instanceof java.lang.classfile.instruction.NewMultiArrayInstruction)
                && ins.get(params + 1).opcode() == Opcode.ARETURN;
    }

    /**
     * The element kind of an array receiver, from its descriptor, checked
     * against the kinds the opcode accepts ({@code accepted}; null for any).
     */
    private ArrayModel.ElementKind receiverKind(AstNode<StmtExpr> arr, List<ArrayModel.ElementKind> accepted) {
        String d = b.descOf.get(arr);
        if (d == null) {
            throw new JavaViolationException(b.untypedLoads.contains(arr)
                    ? ArrayModel.UNTYPED_LOCAL : ArrayModel.UNKNOWN_ELEMENT);
        }
        ArrayModel.ElementKind k = ArrayModel.kindOfDescriptor(d);
        if (k == null && d.startsWith("[")) {
            // Throws the model's reason for this array type.
            ArrayModel.kindOfType(TypeRef.ofDescriptor(d));
        }
        if (k == null || (accepted != null && !accepted.contains(k))) {
            throw new JavaViolationException(ArrayModel.UNKNOWN_ELEMENT);
        }
        return k;
    }

    /** Callee edge, demand, and purity bookkeeping for a call of JArray member {@code name}. */
    private void record(ContractMember member, AstNode<StmtExpr> call, String name) {
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(member.calleeKey());
        }
        ctx.contracts.demand(member);
        b.calleeKeys.put(call.val(), ArrayModel.SORT + "_" + name);
    }

    // --- Compound assignment and value uses ---

    /**
     * {@code dup2} before an {@code xaload} ({@code a[i] op= v}, {@code
     * a[i]++}): push the array and index again, so the load and the store
     * each get them.
     *
     * @throws JavaViolationException {@link ArrayModel#EFFECTFUL_COMPOUND}
     *         unless both are side-effect free ({@link BodyTranslator#isSimple})
     */
    void duplicateIndexedPair() {
        AstNode<StmtExpr> index = b.stack.pop();
        AstNode<StmtExpr> arr = b.stack.peek();
        b.stack.push(index);
        if (!b.isSimple(arr) || !b.isSimple(index)) {
            throw new JavaViolationException(ArrayModel.EFFECTFUL_COMPOUND);
        }
        b.stack.push(arr);
        b.stack.push(index);
    }

    /**
     * Refuse a {@code dup_x1}/{@code dup_x2}/{@code dup2_x1}/{@code dup2_x2}
     * that copies a value beneath an array reference: javac's form of an
     * element assignment or increment whose value is used ({@code x = a[i]++},
     * {@code x = (a[i] = v)}).
     */
    void refuseValueUse(int depth) {
        if (depth == 0 || !enabled()) {
            return;
        }
        var it = b.stack.iterator();
        if (!it.hasNext()) {
            return;
        }
        it.next();
        for (int i = 0; i < depth && it.hasNext(); i++) {
            String d = b.descOf.get(it.next());
            if (d != null && d.startsWith("[")) {
                throw new JavaViolationException(ArrayModel.VALUE_USE);
            }
        }
    }

    // --- Placement ---

    /**
     * Before an allocation or a write emits a statement: refuse where it
     * would not run exactly once, in Java's order. In order: a conditional
     * or repeated context, a specification, contract code, a {@code @Pure}
     * program method, and a pending stack entry that is not simple (its
     * evaluation would move after the emitted statements).
     */
    private void checkPlacement() {
        if (b.inConditional()) {
            throw new JavaViolationException(ArrayModel.CONDITIONAL);
        }
        if (ctx.method.inContract) {
            throw new JavaViolationException(ArrayModel.SPEC_WRITE);
        }
        if (ctx.contractMode) {
            throw new JavaViolationException(ArrayModel.CONTRACT_WRITE);
        }
        if (ctx.method.pure) {
            throw new JavaViolationException(ArrayModel.PURE_WRITE);
        }
        for (AstNode<StmtExpr> pending : b.stack) {
            if (!b.isSimple(pending)) {
                throw new JavaViolationException(ArrayModel.ORDER);
            }
        }
    }

    // --- Specifications ---

    /** True iff {@code n}'s subtree calls a JArray member or an array wrapper. */
    boolean mentionsArrayCall(AstNode<StmtExpr> n) {
        String key = b.calleeKeys.get(n.val());
        if (key != null && key.startsWith(ArrayModel.SORT + "_")) {
            return true;
        }
        if (n.val() instanceof StmtExpr.StaticCall c && ArrayModel.wrappedMember(c.callee().text()) != null) {
            return true;
        }
        for (AstNode<StmtExpr> child : BodyTranslator.childrenOf(n.val())) {
            if (mentionsArrayCall(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code n} with every {@code java?array_load_*(x)} replaced by {@code x}:
     * specifications read raw elements (Strata does not link an opaque
     * helper inside a condition), which only drops the range assumption.
     * Returns {@code n} itself when it has no such call.
     */
    static AstNode<StmtExpr> stripLoadFacts(AstNode<StmtExpr> n) {
        return rewriteLoads(n, (load, x) -> x);
    }

    /**
     * {@code n}, a transparent {@code @Pure} body, with every {@code
     * java?array_load_<k>(x)} replaced by the transparent {@code
     * java?array_pure_load_<k>(x)} ({@link LaurelFactory#registerPureArrayLoadHelper}):
     * Strata turns the body into a function, which cannot call the opaque
     * helper, and the result keeps the element's range type, which a raw
     * read would lose (a range-constrained result or local of the body would
     * then fail its range check). Returns {@code n} itself when it has no
     * such call.
     */
    static AstNode<StmtExpr> pureBodyLoads(AstNode<StmtExpr> n, LaurelFactory lf) {
        return rewriteLoads(n, (load, x) -> {
            var c = (StmtExpr.StaticCall) load.val();
            String suffix = c.callee().text().substring(ArrayModel.LOAD_HELPER_PREFIX.length());
            String helper = lf.registerPureArrayLoadHelper(ArrayModel.kindOfHelperSuffix(suffix));
            return new AstNode<>(new StmtExpr.StaticCall(LaurelFactory.ident(helper), List.of(x), c.typeArgs()),
                    load.source());
        });
    }

    /**
     * {@code n} with every {@code java?array_load_*(x)} call node replaced by
     * {@code onLoad(node, x')}, where {@code x'} is {@code x} rewritten.
     * Returns {@code n} itself when it has no such call.
     */
    private static AstNode<StmtExpr> rewriteLoads(AstNode<StmtExpr> n, LoadRewrite onLoad) {
        if (n.val() instanceof StmtExpr.StaticCall c && c.arguments().size() == 1
                && c.callee().text().startsWith(ArrayModel.LOAD_HELPER_PREFIX)) {
            return onLoad.apply(n, rewriteLoads(c.arguments().get(0), onLoad));
        }
        StmtExpr v = n.val();
        StmtExpr rewritten = switch (v) {
            case StmtExpr.StaticCall c -> {
                var args = rewriteAll(c.arguments(), onLoad);
                yield args == c.arguments() ? v : new StmtExpr.StaticCall(c.callee(), args, c.typeArgs());
            }
            case StmtExpr.InstanceCall c -> {
                var target = rewriteLoads(c.target(), onLoad);
                var args = rewriteAll(c.arguments(), onLoad);
                yield target == c.target() && args == c.arguments() ? v
                        : new StmtExpr.InstanceCall(target, c.callee(), args);
            }
            case StmtExpr.IfThenElse c -> {
                var cond = rewriteLoads(c.cond(), onLoad);
                var then = rewriteLoads(c.thenBranch(), onLoad);
                var els = c.elseBranch().map(e -> rewriteLoads(e, onLoad));
                yield cond == c.cond() && then == c.thenBranch()
                        && (c.elseBranch().isEmpty() || els.get() == c.elseBranch().get()) ? v
                        : new StmtExpr.IfThenElse(cond, then, els);
            }
            case StmtExpr.Quantifier q -> {
                var body = rewriteLoads(q.body(), onLoad);
                yield body == q.body() ? v : new StmtExpr.Quantifier(q.mode(), q.param(), q.trigger(), body);
            }
            case StmtExpr.Old o -> {
                var value = rewriteLoads(o.value(), onLoad);
                yield value == o.value() ? v : new StmtExpr.Old(value, o.label());
            }
            case StmtExpr.ReferenceEquals r -> {
                var lhs = rewriteLoads(r.lhs(), onLoad);
                var rhs = rewriteLoads(r.rhs(), onLoad);
                yield lhs == r.lhs() && rhs == r.rhs() ? v : new StmtExpr.ReferenceEquals(lhs, rhs);
            }
            case StmtExpr.Var var when var.var_() instanceof Variable.Field f -> {
                var target = rewriteLoads(f.target(), onLoad);
                yield target == f.target() ? v : new StmtExpr.Var(new Variable.Field(target, f.fieldName()));
            }
            // The statements of a transparent @Pure body.
            case StmtExpr.Block blk -> {
                var stmts = rewriteAll(blk.statements(), onLoad);
                yield stmts == blk.statements() ? v : new StmtExpr.Block(stmts, blk.label());
            }
            case StmtExpr.Return r -> {
                var value = r.value().map(e -> rewriteLoads(e, onLoad));
                yield r.value().isEmpty() || value.get() == r.value().get() ? v : new StmtExpr.Return(value);
            }
            case StmtExpr.Assign a -> {
                var value = rewriteLoads(a.value(), onLoad);
                yield value == a.value() ? v : new StmtExpr.Assign(a.targets(), value);
            }
            case StmtExpr.Assert a -> {
                var cond = rewriteLoads(a.condition(), onLoad);
                yield cond == a.condition() ? v : new StmtExpr.Assert(cond, a.summary());
            }
            case StmtExpr.Assume a -> {
                var cond = rewriteLoads(a.condition(), onLoad);
                yield cond == a.condition() ? v : new StmtExpr.Assume(cond);
            }
            default -> v;
        };
        return rewritten == v ? n : new AstNode<>(rewritten, n.source());
    }

    private static List<AstNode<StmtExpr>> rewriteAll(List<AstNode<StmtExpr>> nodes, LoadRewrite onLoad) {
        List<AstNode<StmtExpr>> out = null;
        for (int i = 0; i < nodes.size(); i++) {
            var s = rewriteLoads(nodes.get(i), onLoad);
            if (s != nodes.get(i) && out == null) {
                out = new ArrayList<>(nodes.subList(0, i));
            }
            if (out != null) {
                out.add(s);
            }
        }
        return out == null ? nodes : out;
    }

    /** The replacement of a load helper call node, given the node and its rewritten argument. */
    @FunctionalInterface
    private interface LoadRewrite {
        AstNode<StmtExpr> apply(AstNode<StmtExpr> load, AstNode<StmtExpr> x);
    }

    // --- Node helpers ---

    private static boolean isLocal(AstNode<StmtExpr> n) {
        return n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local;
    }

    private static StmtExpr local(String name) {
        return LaurelFactory.var_(name);
    }

    private StmtExpr declare(String name, HighType type, AstNode<StmtExpr> value) {
        var param = new ParameterOpt(LaurelFactory.ident(name), Optional.of(LaurelFactory.node(type)));
        return new StmtExpr.Assign(List.of(new AstNode<>(new Variable.Declare(param), SourceMap.UNKNOWN_RANGE)),
                value);
    }
}

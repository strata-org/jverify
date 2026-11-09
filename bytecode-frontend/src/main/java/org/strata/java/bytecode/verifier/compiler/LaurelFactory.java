package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Condition;
import org.strata.jverify.laurel.ConditionMode;
import org.strata.jverify.laurel.ConstrainedType;
import org.strata.jverify.laurel.CoroutineContracts;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Identifier;
import org.strata.jverify.laurel.ModifiesGroup;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.laurel.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Laurel node-construction service: the builders every emission site uses
 * ({@code node}/{@code ident}/{@code primitiveOp}/literals), the predefined
 * constrained-integer prelude, and the cast-helper procedure registry.
 *
 * <p>Extracted verbatim from {@code BytecodeToLaurelCompiler}. Instance
 * methods default a node's range to the enclosing statement's via the
 * {@link SourceMap}.
 */
final class LaurelFactory {

    /** Strata's reserved output-parameter name for the short return form. */
    static final String LAUREL_RESULT_BINDING = "$result";

    /**
     * Synthesized receiver parameter on instance procedures. '?' is outside
     * Java's identifier grammar but inside Strata's, so no user local can
     * collide; Strata's LiftInstanceProcedures prepends the receiver
     * argument, so the lifted procedure must declare it first.
     */
    static final String LAUREL_SELF_BINDING = "self?";

    /**
     * Synthesized name binding the thrown value in a `throws (e: T)` clause.
     * Uses '?' for the same reason {@link #LAUREL_SELF_BINDING} does: '?' is
     * outside Java's identifier grammar but inside Strata's, so no user name
     * can spell this one. A `java$`-style prefix would not have that property
     * -- `$` is legal in a Java identifier (JLS 3.8) and only a *leading* `$`
     * is escaped, so a user writing `int java$thrown` could collide with the
     * generated binder.
     */
    static final String THROWN_BINDING = "java?thrown";

    private final SourceMap src;

    /** Cast-helper procedures, keyed by mangled name, in first-use order. */
    final LinkedHashMap<String, Procedure> castHelpers = new LinkedHashMap<>();

    LaurelFactory(SourceMap src) {
        this.src = src;
    }

    // --- Node helpers ---

    /**
     * Wrap modifies {@code targets} into the degenerate group form every
     * frontend emits: one unguarded {@link ModifiesGroup} that is always
     * present, even when the target list is empty (an empty group is the
     * opaque default "nothing changes" frame -- dropping it would leave the
     * procedure unframed).
     */
    static List<ModifiesGroup> unguardedModifies(List<AstNode<StmtExpr>> targets) {
        return List.of(new ModifiesGroup(targets, Optional.empty(), Optional.empty()));
    }

    /**
     * Java allows {@code $} in identifiers (JLS 3.8); Laurel reserves a *leading*
     * {@code $} for compiler-generated names and rejects any source declaration that
     * starts with one. So a leading {@code $} that came from Java is escaped to
     * {@code @}: Java identifiers cannot contain {@code @} (JLS 3.8), which makes the
     * mapping injective -- the same property {@code MANGLE_SEPARATOR} and
     * {@code LAUREL_SELF_BINDING} rely on for {@code '?'}.
     *
     * <p>Only the first character is rewritten. A {@code $} further along a name is
     * legal in Laurel source, so {@code a$b} and javac's {@code val$x} capture fields
     * pass through as they are.
     */
    private static String laurelSafeName(String name) {
        return name.startsWith("$") ? "@" + name.substring(1) : name;
    }

    /**
     * A Laurel identifier for a name Strata itself dictates -- a prelude operator
     * procedure ({@code $add}, {@code $eq}, …) or the sole-output binding
     * {@code $result}. Never escaped, because JVerify chose it rather than reading it
     * out of the user's Java.
     *
     * <p>Distinguishing by provenance rather than spelling is the point: a user may
     * legally declare {@code int $eq;} or {@code int $result;}, and those must still be
     * escaped by {@link #ident} even though they collide with a dictated spelling.
     */
    static Identifier reservedIdent(String name) {
        return new Identifier(name, Optional.empty(), SourceMap.UNKNOWN_RANGE);
    }

    /** {@link #var_} for a Strata-dictated name; see {@link #reservedIdent}. */
    static StmtExpr reservedVar_(String name) {
        return new StmtExpr.Var(new Variable.Local(reservedIdent(name)));
    }

    static Identifier ident(String name) {
        return new Identifier(laurelSafeName(name), Optional.empty(), SourceMap.UNKNOWN_RANGE);
    }

    /**
     * Refuse a user binder literally spelled {@code $result}. Strata's short
     * {@code : T} return form generates an output parameter named {@code $result}
     * (emitted unescaped via {@link #reservedIdent}) for every returning procedure,
     * so a user binder of that name would collide inside Strata.
     * {@code laurelSafeName} would escape it to {@code @result} and translate
     * cleanly, but the frontend deliberately refuses with an attributable
     * diagnostic, and the corpus pins that outcome (skip with this reason). Only
     * the exact spelling {@code $result} is reserved -- a leading {@code $} on any
     * other name is escaped, not refused.
     *
     * <p>Call at binder sites only (params, locals, lambda/quantifier binders), never
     * inside {@link #ident}, which also escapes non-binder names (method/class/type).
     *
     * <p>The collision exists only because the generated output binding is spelled
     * {@code $result}, a name a Java user can legally take. Renaming it into the
     * {@code ?} namespace ({@code java?result}, as {@link #LAUREL_SELF_BINDING} and
     * {@link #THROWN_BINDING} already are, and Strata accepts any sole-output name)
     * would make this gate unnecessary and close the one gap it cannot reach: a
     * bodyless method's parameters carry no name in bytecode (no Code, no LVT), so
     * this gate can never fire for them. That rename would flip the affected
     * Skipped methods to verified.
     */
    static void refuseReservedName(String name) {
        if (LAUREL_RESULT_BINDING.equals(name)) {
            throw new JavaViolationException(
                    "identifier '" + LAUREL_RESULT_BINDING + "' is reserved by the verifier");
        }
    }

    /**
     * The Laurel identifier text for a local that the postcondition-lambda rewrite may
     * have renamed. A rename *target* ({@code $result}) is JVerify's own choice, so it
     * passes through unescaped; an unrenamed name came from the user's Java and is
     * escaped by {@link #laurelSafeName}. Telling the two apart by provenance is what
     * keeps a user local spelled {@code $result} distinct from the generated one.
     *
     * <p>Returns the text rather than an {@link Identifier} because one caller compares
     * it against an already-built identifier; building and escaping twice would not
     * round-trip.
     */
    static String laurelLocalName(java.util.Map<String, String> renames, String name) {
        String renamed = renames.get(name);
        return renamed != null ? renamed : laurelSafeName(name);
    }

    /** {@link #ident} for a possibly-renamed local; see {@link #laurelLocalName}. */
    static Identifier identForLocal(java.util.Map<String, String> renames, String name) {
        return reservedIdent(laurelLocalName(renames, name));
    }

    /** {@link #var_} for a possibly-renamed local; see {@link #laurelLocalName}. */
    static StmtExpr varForLocal(java.util.Map<String, String> renames, String name) {
        return new StmtExpr.Var(new Variable.Local(identForLocal(renames, name)));
    }

    AstNode<StmtExpr> node(StmtExpr expr) {
        return new AstNode<>(expr, src.defaultRange);
    }

    AstNode<StmtExpr> node(StmtExpr expr, FileRange range) {
        return new AstNode<>(expr, range);
    }

    AstNode<Variable> nodeVar(Variable v) {
        return new AstNode<>(v, src.defaultRange);
    }

    static AstNode<HighType> node(HighType type) {
        return new AstNode<>(type, SourceMap.UNKNOWN_RANGE);
    }

    static StmtExpr var_(String name) {
        return new StmtExpr.Var(new Variable.Local(ident(name)));
    }

    /**
     * Java copy of Strata's {@code Operation.procName}. Must stay in sync with
     * Strata: a renamed or added operator there breaks resolution of the
     * emitted call.
     */
    static String operationProcName(Operation op) {
        return switch (op) {
            case Operation.Eq _ -> "$eq";
            case Operation.Neq _ -> "$neq";
            case Operation.And _ -> "$and";
            case Operation.Or _ -> "$or";
            case Operation.Not _ -> "$not";
            case Operation.Implies _ -> "$implies";
            case Operation.AndThen _ -> "$andThen";
            case Operation.OrElse _ -> "$orElse";
            case Operation.Neg _ -> "$neg";
            case Operation.Add _ -> "$add";
            case Operation.Sub _ -> "$sub";
            case Operation.Mul _ -> "$mul";
            case Operation.Div _ -> "$div";
            case Operation.Mod _ -> "$mod";
            case Operation.DivT _ -> "$divT";
            case Operation.ModT _ -> "$modT";
            case Operation.Lt _ -> "$lt";
            case Operation.Leq _ -> "$le";
            case Operation.Gt _ -> "$gt";
            case Operation.Geq _ -> "$ge";
            case Operation.StrConcat _ -> "$strConcat";
        };
    }

    /** Every name {@link #operationProcName} returns. */
    private static final java.util.Set<String> OPERATION_PROC_NAMES = java.util.Set.of(
            "$eq", "$neq", "$and", "$or", "$not", "$implies", "$andThen", "$orElse", "$neg", "$add", "$sub",
            "$mul", "$div", "$mod", "$divT", "$modT", "$lt", "$le", "$gt", "$ge", "$strConcat");

    /** True iff {@code callee} is a prelude operator procedure ({@link #operationProcName}). */
    static boolean isOperationProcName(String callee) {
        return OPERATION_PROC_NAMES.contains(callee);
    }

    @SafeVarargs
    static StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return staticCall(reservedIdent(operationProcName(op)), List.of(args), List.of());
    }

    static StmtExpr staticCall(Identifier callee, List<AstNode<StmtExpr>> args, List<AstNode<HighType>> typeArgs) {
        return new StmtExpr.Call(Optional.empty(), callee, args, typeArgs);
    }

    static StmtExpr instanceCall(AstNode<StmtExpr> receiver, Identifier callee, List<AstNode<StmtExpr>> args,
            List<AstNode<HighType>> typeArgs) {
        return new StmtExpr.Call(Optional.of(receiver), callee, args, typeArgs);
    }

    static AstNode<StmtExpr> noRange(StmtExpr e) {
        return new AstNode<>(e, SourceMap.UNKNOWN_RANGE);
    }

    static StmtExpr longLiteral(long val) {
        if (val >= 0 || val == Long.MIN_VALUE) {
            return new StmtExpr.LiteralInt(val);
        }
        return primitiveOp(new Operation.Neg(), noRange(new StmtExpr.LiteralInt(-val)));
    }

    // --- Predefined constrained integer types ---

    static final LinkedHashMap<String, Long[]> BOUNDS = new LinkedHashMap<>();

    static {
        BOUNDS.put("int8", new Long[]{-128L, 127L});
        BOUNDS.put("int16", new Long[]{-32768L, 32767L});
        BOUNDS.put("int32", new Long[]{-2147483648L, 2147483647L});
        BOUNDS.put("int64", new Long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        BOUNDS.put("char", new Long[]{0L, 65535L});
        BOUNDS.put("nat7", new Long[]{0L, 127L});
        BOUNDS.put("nat15", new Long[]{0L, 32767L});
        BOUNDS.put("nat31", new Long[]{0L, 2147483647L});
        BOUNDS.put("nat63", new Long[]{0L, Long.MAX_VALUE});
        BOUNDS.put("nat", new Long[]{0L, null});
    }

    static List<TypeDefinition> getPredefinedTypes() {
        var types = new ArrayList<TypeDefinition>();
        for (var entry : BOUNDS.entrySet()) {
            types.add(makeConstrainedType(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return types;
    }

    private static TypeDefinition makeConstrainedType(String name, Long min, Long max) {
        var x = var_("x");
        var bounds = new ArrayList<StmtExpr>();
        if (min != null) {
            bounds.add(primitiveOp(new Operation.Geq(), noRange(x), noRange(longLiteral(min))));
        }
        if (max != null) {
            bounds.add(primitiveOp(new Operation.Leq(), noRange(x), noRange(longLiteral(max))));
        }
        var constraint = bounds.stream()
                .reduce((a, b) -> primitiveOp(new Operation.And(), noRange(a), noRange(b)))
                .orElse(new StmtExpr.LiteralBool(true));
        var witness = new StmtExpr.LiteralInt(0);
        return new TypeDefinition.Constrained(new ConstrainedType(
                ident(name), node(new HighType.TInt()), ident("x"), noRange(constraint), noRange(witness)));
    }

    // --- Cast helpers ---

    static String laurelIntegerTypeName(HighType targetType) {
        if (targetType instanceof HighType.UserDefined u && BOUNDS.get(u.name().text()) != null) {
            return u.name().text();
        }
        if (targetType instanceof HighType.TInt) {
            return "int";
        }
        return null;
    }

    static String laurelTypeName(HighType ty) {
        if (ty instanceof HighType.UserDefined u) {
            return u.name().text();
        }
        if (ty instanceof HighType.TInt) {
            return "int";
        }
        throw new JavaViolationException("Unsupported cast source type: " + ty);
    }

    void registerCastHelper(String helperName, String sourceName, HighType sourceType,
                            String targetName, HighType targetType) {
        if (castHelpers.containsKey(helperName)) {
            return;
        }
        var x = var_("x");
        Long[] bounds = BOUNDS.get(targetName);
        var castLabel = Optional.of("cast from " + sourceName + " to " + targetName);
        var preconditions = new ArrayList<Condition>();
        if (bounds != null) {
            if (bounds[0] != null) {
                preconditions.add(new Condition(
                        noRange(primitiveOp(new Operation.Geq(), noRange(x), noRange(longLiteral(bounds[0])))),
                        castLabel, new ConditionMode.Both()));
            }
            if (bounds[1] != null) {
                preconditions.add(new Condition(
                        noRange(primitiveOp(new Operation.Leq(), noRange(x), noRange(longLiteral(bounds[1])))),
                        castLabel, new ConditionMode.Both()));
            }
        }
        var resultEqX = primitiveOp(new Operation.Eq(), noRange(reservedVar_(LAUREL_RESULT_BINDING)), noRange(x));
        var postconditions = List.of(
                new Condition(noRange(resultEqX), Optional.empty(), new ConditionMode.Both()));
        var proc = new Procedure(
                ident(helperName),
                /*typeArgs=*/List.of(),
                List.of(new org.strata.jverify.laurel.Parameter(ident("x"), node(sourceType))),
                List.of(new org.strata.jverify.laurel.Parameter(reservedIdent(LAUREL_RESULT_BINDING), node(targetType))),
                preconditions,
                new CoroutineContracts.Regular(),
                Optional.empty(),
                new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(List.of())),
                Optional.empty(), false, List.of(),
                Optional.empty(), Optional.empty(), /*throwsInferred=*/false, List.of(),
                /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
        castHelpers.put(helperName, proc);
    }

    // --- Checked integer arithmetic helpers ---

    /**
     * Register, once, the checked arithmetic helper for {@code op} on the
     * int type named {@code typeName} ({@code int32} or {@code int64}):
     * {@code java?<opKey>_<typeName>(x: int[, y: int]): int}, transparent
     * {@code x <op> y}, with {@code requires} clauses bounding the
     * mathematical result to the type's range (summary
     * {@code "no <int|long> overflow on '<symbol>'"}).
     *
     * <p>The body is TRANSPARENT, not opaque with {@code ensures $result ==
     * x + y}: a transparent body makes the call's value the mathematical
     * result unconditionally wherever the call appears. In a contract
     * helper's {@code $asFunction} twin an opaque helper's {@code ensures}
     * is not available (TransparencyPass erases the assumes that would
     * justify it), so an opaque encoding would leave postconditions that
     * mention checked arithmetic unprovable. The {@code requires} is the
     * overflow proof obligation, checked wherever Strata checks call
     * preconditions — method bodies, contracts, and conditionally evaluated
     * positions (guarded, like {@code $divT}'s divisor). Same pattern as
     * {@code java?array_get}'s transparent body with a bounds requires.
     *
     * @return the helper's name
     */
    String registerArithHelper(String opKey, String symbol, Operation op, boolean unary, String typeName) {
        String name = "java?" + opKey + "_" + typeName;
        if (!castHelpers.containsKey(name)) {
            Long[] bounds = BOUNDS.get(typeName);
            String javaName = typeName.equals("int64") ? "long" : "int";
            var label = Optional.of("no " + javaName + " overflow on '" + symbol + "'");
            java.util.function.Supplier<StmtExpr> result = () -> unary
                    ? primitiveOp(op, noRange(var_("x")))
                    : primitiveOp(op, noRange(var_("x")), noRange(var_("y")));
            var preconditions = List.of(
                    new Condition(noRange(primitiveOp(new Operation.Geq(),
                            noRange(result.get()), noRange(longLiteral(bounds[0])))), label, new ConditionMode.Both()),
                    new Condition(noRange(primitiveOp(new Operation.Leq(),
                            noRange(result.get()), noRange(longLiteral(bounds[1])))), label, new ConditionMode.Both()));
            var inputs = unary ? List.of(param("x", new HighType.TInt()))
                    : List.of(param("x", new HighType.TInt()), param("y", new HighType.TInt()));
            // The body is a Block ending in Return — the shape a translated
            // method body has — rather than a bare expression, which the
            // Laurel-to-Core lowering cannot turn into a function (the Core
            // interpreter then gets stuck on the unreduced call).
            var body = new Body.Transparent(noRange(new StmtExpr.Block(
                    List.of(noRange(new StmtExpr.Return(List.of(noRange(result.get()))))),
                    Optional.empty(), List.of(), Optional.empty())));
            castHelpers.put(name, helperProcedure(name, List.of(), inputs, new HighType.TInt(),
                    preconditions, body));
        }
        return name;
    }

    /** The {@code Operation} behind a checked-arithmetic helper name, or null if not one. */
    static Operation arithHelperOp(String callee) {
        if (!callee.startsWith("java?")) {
            return null;
        }
        return switch (callee.substring("java?".length())) {
            case "add_int32", "add_int64" -> new Operation.Add();
            case "sub_int32", "sub_int64" -> new Operation.Sub();
            case "mul_int32", "mul_int64" -> new Operation.Mul();
            case "neg_int32", "neg_int64" -> new Operation.Neg();
            default -> null;
        };
    }

    // --- Array element helpers and wrappers ---

    /**
     * Register, once, kind {@code k}'s element helpers (none for boolean):
     * {@code java?array_store_<suffix>(x: int): int}, which requires
     * {@code x} in the kind's range (summary {@code "<kind>[] element range"})
     * and returns it, and {@code java?array_load_<suffix>(x: int): <range
     * type>}, which returns {@code x} as the constrained type, so a load is
     * assumed in range. Both are opaque.
     *
     * <p>Temporary: the element range belongs to Strata, which cannot carry
     * it yet. Strata rejects constrained types such as {@code int8} as type
     * arguments (B1, {@code checkTypeArgNotConstrained} in
     * {@code Resolution.lean}), and an {@code int32} argument for a
     * {@code T} parameter fails after monomorphization (B2), so arrays carry
     * {@code int}. Once both are fixed, arrays are
     * {@code JArray<int8|int16|int32|int64|char>}, Strata checks {@code set}
     * and assumes {@code get} as for a constrained field, and these helpers
     * and {@link #registerPureArrayLoadHelper} go.
     */
    void registerArrayElementHelpers(ArrayModel.ElementKind k) {
        if (k.helperSuffix() == null) {
            return;
        }
        var x = var_("x");
        var resultEqX = new Condition(noRange(primitiveOp(new Operation.Eq(),
                noRange(reservedVar_(LAUREL_RESULT_BINDING)), noRange(x))), Optional.empty(), new ConditionMode.Both());
        String store = ArrayModel.STORE_HELPER_PREFIX + k.helperSuffix();
        if (!castHelpers.containsKey(store)) {
            var label = Optional.of(k.javaName() + "[] element range");
            var preconditions = List.of(
                    new Condition(noRange(primitiveOp(new Operation.Geq(), noRange(x), noRange(longLiteral(k.lo())))),
                            label, new ConditionMode.Both()),
                    new Condition(noRange(primitiveOp(new Operation.Leq(), noRange(x), noRange(longLiteral(k.hi())))),
                            label, new ConditionMode.Both()));
            castHelpers.put(store, helperProcedure(store, List.of(), List.of(param("x", new HighType.TInt())),
                    new HighType.TInt(), preconditions,
                    new Body.Opaque(List.of(resultEqX), Optional.empty(), unguardedModifies(List.of()))));
        }
        String load = ArrayModel.LOAD_HELPER_PREFIX + k.helperSuffix();
        if (!castHelpers.containsKey(load)) {
            castHelpers.put(load, helperProcedure(load, List.of(), List.of(param("x", new HighType.TInt())),
                    new HighType.UserDefined(ident(k.rangeType())), List.of(),
                    new Body.Opaque(List.of(resultEqX), Optional.empty(), unguardedModifies(List.of()))));
        }
    }

    /**
     * Register, once, kind {@code k}'s load helper for a specification or a
     * transparent {@code @Transparent} body, which Strata turns into a function
     * that cannot call the opaque {@code java?array_load_<suffix>}: the transparent {@code
     * java?array_pure_load_<suffix>(x: int): <range type>} = {@code if lo <= x
     * && x <= hi then x else 0}. Every stored element is range-checked, so it
     * returns {@code x}, and its result is in range by definition. Temporary,
     * like {@link #registerArrayElementHelpers}: it goes once Strata accepts
     * constrained type arguments (B1, B2).
     *
     * @return the helper's name
     */
    String registerPureArrayLoadHelper(ArrayModel.ElementKind k) {
        String name = ArrayModel.PURE_LOAD_HELPER_PREFIX + k.helperSuffix();
        if (!castHelpers.containsKey(name)) {
            var x = noRange(var_("x"));
            var inRange = noRange(primitiveOp(new Operation.And(),
                    noRange(primitiveOp(new Operation.Leq(), noRange(longLiteral(k.lo())), x)),
                    noRange(primitiveOp(new Operation.Leq(), x, noRange(longLiteral(k.hi()))))));
            castHelpers.put(name, helperProcedure(name, List.of(), List.of(param("x", new HighType.TInt())),
                    new HighType.UserDefined(ident(k.rangeType())), List.of(),
                    returnBody(noRange(new StmtExpr.IfThenElse(inRange, x,
                            Optional.of(noRange(new StmtExpr.LiteralInt(0))))))));
        }
        return name;
    }
    private static Body returnBody(AstNode<StmtExpr> value) {
        return new Body.Transparent(noRange(new StmtExpr.Return(List.of(value))));
    }

    private static org.strata.jverify.laurel.Parameter param(String name, HighType type) {
        return new org.strata.jverify.laurel.Parameter(ident(name), node(type));
    }

    private static Procedure helperProcedure(String name, List<Identifier> typeArgs,
                                             List<org.strata.jverify.laurel.Parameter> inputs, HighType result,
                                             List<Condition> preconditions, Body body) {
        return new Procedure(ident(name), typeArgs, inputs,
                List.of(new org.strata.jverify.laurel.Parameter(reservedIdent(LAUREL_RESULT_BINDING), node(result))),
                preconditions, new CoroutineContracts.Regular(), Optional.empty(), body, Optional.empty(), false, List.of(),
                Optional.empty(), Optional.empty(), /*throwsInferred=*/false, List.of(),
                /*readsGlobals=*/List.of(), /*writesGlobals=*/List.of());
    }
}

package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.Condition;
import org.strata.jverify.laurel.ConditionMode;
import org.strata.jverify.laurel.ConstrainedType;
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
     * Synthesized receiver parameter on instance procedures (JFE's
     * LAUREL_SELF_BINDING). '?' is outside Java's identifier grammar but
     * inside Strata's, so no user local can collide; Strata's
     * LiftInstanceProcedures prepends the receiver argument, so the lifted
     * procedure must declare it first.
     */
    static final String LAUREL_SELF_BINDING = "self?";

    /**
     * Synthesized name binding the thrown value in a `throws (e: T)` clause (JFE's
     * THROWN_BINDING). Uses '?' for the same reason {@link #LAUREL_SELF_BINDING} does:
     * '?' is outside Java's identifier grammar but inside Strata's, so no user name can
     * spell this one. A `java$`-style prefix would not have that property -- `$` is legal
     * in a Java identifier (JLS 3.8) and only a *leading* `$` is escaped, so a user
     * writing `int java$thrown` could collide with the generated binder.
     */
    static final String THROWN_BINDING = "java?thrown";

    private final SourceMap src;

    /** Cast-helper procedures, keyed by mangled name, in first-use order. */
    final LinkedHashMap<String, Procedure> castHelpers = new LinkedHashMap<>();

    LaurelFactory(SourceMap src) {
        this.src = src;
    }

    // --- Node helpers (mirror JFE's) ---

    /**
     * Wrap modifies {@code targets} into the degenerate group form every
     * frontend emits: one unguarded {@link ModifiesGroup} that is always
     * present, even when the target list is empty (an empty group is the
     * opaque default "nothing changes" frame -- dropping it would leave the
     * procedure unframed). Mirrors JFE's JavaToLaurelCompiler.unguardedModifies.
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

    /** Java mirror of Strata's {@code Operation.procName}; see JFE for the sync warning. */
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

    @SafeVarargs
    static StmtExpr primitiveOp(Operation op, AstNode<StmtExpr>... args) {
        return new StmtExpr.StaticCall(reservedIdent(operationProcName(op)), List.of(args));
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

    // --- Predefined constrained integer types (same BOUNDS as JFE) ---

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
                Optional.empty(),
                new Body.Opaque(postconditions, Optional.empty(), unguardedModifies(List.of())),
                Optional.empty(), false, List.of(),
                Optional.empty(), Optional.empty(), List.of());
        castHelpers.put(helperName, proc);
    }
}

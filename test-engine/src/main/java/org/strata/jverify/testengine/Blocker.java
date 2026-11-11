package org.strata.jverify.testengine;

/**
 * Why a test is currently expected to fail (an {@code xfail}). A test lists ALL
 * of its blockers in {@link JVerifyTest#blockedBy()}. When every listed blocker clears, the test
 * begins to pass and the engine flags it as an unexpected pass (XPASS) so the
 * marker gets removed. Being an enum (not free text) gives blockers checked
 * spelling and a stable, find-usages-able vocabulary. Most values name a
 * concrete gap; the two {@code *_OTHER} buckets are deliberate catch-alls for
 * heterogeneous or multi-cause failures and are candidates for later splitting
 * into named buckets (tracked as a follow-up).
 */
public enum Blocker {
    /** Restore a dropped front-end diagnostic emitter / message key (exit-2 negative tests). */
    FRONTEND_DIAGNOSTIC,
    /**
     * Constructor translation, and with it any field value a caller observes.
     *
     * <p>Composite fields themselves ARE emitted, and a direct field read works:
     * {@code void f(C c) { precondition(c.x == 5); check(c.x == 5); }} verifies.
     * What is missing is the constructor wherever {@code Constructors.eligible}
     * rejects it (an enum, record, generic or inner class, or a body that is not
     * structural): such a constructor is not translated, its contract is never
     * checked, and {@code new C(5)} yields a freshly allocated object whose fields
     * are unconstrained. Every assertion a caller makes about a field after that
     * construction is reported as "assertion could not be proved", even though the
     * constructor's own postcondition is provable.
     *
     * <p>Field INITIALIZERS ({@code private int g = 22;}) are a separate,
     * Strata-side gap; see {@link #STRATA_FIELD_INITIALIZERS}. */
    FRONTEND_FIELDS,
    /** Front-end catch-all: operator / cast / numeric plumbing, impure-equality,
     *  abstract-contracts. Heterogeneous by design; split candidate (follow-up). */
    FRONTEND_OTHER,
    /** Anonymous / local class synthesis. */
    FRONTEND_ANON,
    /** Exception-support gaps deliberately deferred -- throw bodies in
     *  switch EXPRESSIONS, exceptions carrying constructor data, catching
     *  provably-unproducible types (see the Wishlist* fixtures under
     *  tests/javasupport/exceptions). */
    FRONTEND_EXCEPTIONS,
    /**
     * A CLASS type parameter is not translated. A method's own type parameters
     * ARE: they become Laurel {@code TVar}s listed in the procedure's
     * {@code typeArgs}. A class's are not, because composites are emitted with
     * {@code typeArgs = List.of()} -- making them work needs
     * {@code CompositeType.typeArgs} plus {@code HighType.Applied} at every use
     * plus {@code StmtExpr.New.typeArgs}. Symptom: {@code Unsupported type: T}
     * on a member of a generic class.
     *
     * <p>For a generic program the front end DOES translate, the Strata-side gaps
     * are named specifically: {@link #STRATA_NO_TYPE_CONSTRAINTS} (no bound slot on
     * a type parameter), {@link #STRATA_GENERIC_OVERRIDE} (an override may not fix
     * the base's type parameter to a concrete type) and {@link #STRATA_WILDCARDS}.
     * There is deliberately no catch-all "Strata generics" bucket -- Strata has
     * generics, and a generic {@code @Transparent} procedure IS transparent (measured), so
     * a failure that merely involves a type parameter is almost always something
     * else.
     */
    FRONTEND_GENERICS,
    /**
     * Front end: an array shape the bytecode front end refuses to lower --
     * reference-typed element arrays, multi-dimensional arrays and {@code @Nullable}
     * arrays. Distinct from {@link #STRATA_ARRAYS}: these programs never reach Strata.
     */
    FRONTEND_ARRAYS,
    /**
     * Strata: a Laurel type parameter carries NO constraints, so a Java type
     * variable's bound cannot be expressed. {@code LaurelAST}'s
     * {@code typeArgs : List Identifier} is a list of bare names in all five
     * declaring positions ({@code Procedure}, {@code CompositeType},
     * {@code DatatypeDefinition}, {@code TypeAlias}, {@code New}) -- there is no
     * bound slot, so {@code <T extends C>} is inexpressible.
     *
     * <p>A Java type variable is never unconstrained: {@code <T>} means
     * {@code T extends Object}, and JVerify models {@code Object} as a composite
     * sort. So {@code <T> void f(T x) { Object o = x; }} -- legal Java -- relies
     * on {@code T <: Object}, and an explicit bound
     * ({@code <T extends Pet>} calling {@code t.feed()}) needs the same thing.
     *
     * <p>Because Laurel's type parameters are fully parametric, its
     * {@code HighType.TVar} correctly unifies only with itself; the front end
     * emitting a bare {@code TVar} where the bound is used therefore produces
     * ill-typed Laurel. Symptom:
     * {@code Impossible to unify Composite with $__opaque_<proc>_<T>_0}, usually
     * surfacing as a {@code strata-unattributed} malformed diagnostic plus
     * {@code Error: Type checking error}. That rejection is CORRECT parametricity,
     * not a Strata bug -- what is missing is the constraint feature.
     */
    STRATA_NO_TYPE_CONSTRAINTS,
    /** Strata: native arrays. */
    STRATA_ARRAYS,
    /** Strata: IEEE-754 float/double theory. */
    STRATA_FLOAT,
    /** Strata: fresh(). */
    STRATA_FRESH,
    /**
     * Strata: dynamic dispatch does not support an override of a generic method
     * that fixes the base's type parameter to a concrete type. Strata requires an
     * overriding composite to repeat the base's parameters verbatim
     * ({@code SBox<T> extends Box<T>}); Java's model is the opposite -- a class or
     * lambda implements a CONCRETE instantiation ({@code implements MyConsumer<Anything>}),
     * fixing the parameter. So once the front end faithfully emits the
     * instantiation, any implementer of a generic interface at a concrete type
     * hits this. Error: "an override of a generic method whose type parameters
     * differ from the base ... must repeat the base's type parameters verbatim".
     */
    STRATA_GENERIC_OVERRIDE,
    /**
     * Strata: {@code MonomorphizeComposites} leaves a dangling reference to a generic
     * composite, and the failure surfaces as an INTERNAL ERROR rather than a clean
     * diagnostic.
     *
     * <p>Triggered by a RAW generic instantiation -- {@code new HasGeneric()} with no
     * type argument, where {@code HasGeneric<T>} is generic. The pass rewrites the
     * composite to its monomorphised name, after which the raw reference no longer
     * resolves:
     *
     * <pre>
     * Internal error: resolution after 'MonomorphizeComposites' introduced this
     * diagnostic: Resolution failed: '...HasGeneric' is not defined
     * </pre>
     *
     * <p>It also emits a second, SOURCE-LESS {@code strata-unattributed} malformed
     * diagnostic for the same cause, so the run reports two errors for one line.
     *
     * <p>Narrow: measured on the same fixture, an explicit instantiation
     * ({@code new GenericContainer<DummySuper>(..)}), the diamond form
     * ({@code new GenericContainer<>(..)}) and a generic method
     * ({@code <T> T genericIdentity(T)}) all verify. Only the raw form breaks.
     *
     * <p>A front-end-side workaround exists -- a raw type erases {@code T} to
     * {@code Object}, so the front end could emit {@code new HasGeneric<Object>()}
     * instead of the empty {@code typeArgs} it emits today -- but an unresolvable name
     * after a pass should be a clean error either way, so this is owned by Strata.
     */
    STRATA_MONOMORPHIZE_BUG,
    /**
     * Strata: Java wildcard types ({@code ? extends T} / {@code ? super T}) have
     * no representation. The front end faithfully emits generic instantiations,
     * but a wildcard type argument surfaces as {@code Unsupported type: ?}; giving
     * it meaning needs variance support in the target language, not front-end
     * work.
     */
    STRATA_WILDCARDS,
    /**
     * Strata: no null literal and no Option type. A Java {@code @Nullable T}
     * needs a nullable representation (a {@code Null}/{@code NonNull(value)}
     * datatype) and flow-sensitive non-null tracking, which the target language
     * does not yet provide -- the front end cannot emit what does not exist, so
     * a {@code null} literal surfaces as {@code Unsupported constant type tag:
     * BOT}. Owned by Strata, not the front end.
     */
    STRATA_NULLABLE,
    /**
     * Strata: field initializers ({@code private int g = 22;}) are not supported,
     * so the value an initializer establishes is not available to reason with.
     * Distinct from {@link #FRONTEND_FIELDS}, which is the front end not
     * translating constructors at all.
     */
    STRATA_FIELD_INITIALIZERS,
    /** Strata: decreases -> Core measure wiring. */
    STRATA_DECREASES,
    STRATA_LAMBDA_VALUES,
    STRATA_SEQUENCE_EQUALITY,
    STRATA_ABSTRACT_PURE_FUNCTIONS,
    /**
     * Strata: a query needs non-linear integer arithmetic ({@code %} or {@code *} with
     * symbolic operands on both sides) and the solver times out instead of deciding it.
     */
    STRATA_NONLINEAR_ARITHMETIC,
    /** Strata catch-all: regressions, trait-redecl, loop-elim, HOF/streams, etc.
     *  Heterogeneous by design; split candidate (follow-up). */
    STRATA_OTHER,
    LANGREF_UNSUPPORTED,
    LANGREF_INTERP_ERROR,
    LANGREF_RESOLUTION_ERROR,
    LANGREF_ASSERT_FAILED,
    LANGREF_ERROR,
    /** Cortex: a construct the translation to Cortex refuses (null, arrays, static fields, ...). */
    CORTEX_UNSUPPORTED,
    /** Cortex: a call to a library method, which the translation to Cortex refuses in interpret mode. */
    CORTEX_LIBRARY_CALL
}

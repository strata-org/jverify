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
    /** @Pure structural/purity validator not yet re-implemented. */
    FRONTEND_PURE_VALIDATOR,
    /** Composite field emission (self#/obj# access). */
    FRONTEND_FIELDS,
    /** Instance (non-static) methods are not translated at all (JavaToLaurelCompiler only
     *  handles STATIC methods), so their contracts/obligations are never emitted or verified. */
    FRONTEND_INSTANCE_METHODS,
    /** Polymorphic dispatch refusal (incl. over-refusal of monomorphic calls). */
    FRONTEND_DISPATCH,
    /** J-side @Nullable / Option synthesis. */
    FRONTEND_NULLABLE,
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
    /** Overload name mangling. */
    FRONTEND_OVERLOAD,
    /**
     * A CLASS type parameter is not translated. A method's own type parameters
     * ARE: they become Laurel {@code TVar}s listed in the procedure's
     * {@code typeArgs}. A class's are not, because composites are emitted with
     * {@code typeArgs = List.of()} -- making them work needs
     * {@code CompositeType.typeArgs} plus {@code HighType.Applied} at every use
     * plus {@code StmtExpr.New.typeArgs}. Symptom: {@code Unsupported type: T}
     * on a member of a generic class.
     *
     * <p>Distinct from {@link #STRATA_GENERICS}, which is for programs the
     * front end DOES translate and Strata then cannot handle.
     */
    FRONTEND_GENERICS,
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
    /**
     * Strata: generics / type application, other than the missing type-parameter
     * constraints of {@link #STRATA_NO_TYPE_CONSTRAINTS}. Use this only when the
     * front end translated the generics and Strata is what fails -- e.g. a
     * {@code @Pure} generic procedure that does not behave transparently, so a
     * caller cannot see its body.
     */
    STRATA_GENERICS,
    /** Strata: native arrays. */
    STRATA_ARRAYS,
    /** Strata: IEEE-754 float/double theory. */
    STRATA_FLOAT,
    /** Strata: fresh(). */
    STRATA_FRESH,
    /** Strata: decreases -> Core measure wiring. */
    STRATA_DECREASES,
    /** Strata catch-all: regressions, trait-redecl, loop-elim, HOF/streams, etc.
     *  Heterogeneous by design; split candidate (follow-up). */
    STRATA_OTHER
}

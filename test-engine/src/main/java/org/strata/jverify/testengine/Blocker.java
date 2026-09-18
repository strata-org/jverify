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
    /** Strata: generics / type application. */
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

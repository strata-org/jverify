package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Impure;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * {@code ==} on a TYPE VARIABLE is restricted exactly as it is on {@code Object}.
 *
 * <p>A type variable says as little about its runtime class as {@code Object} does, so
 * {@code T} may be instantiated at {@code String}, a boxed primitive or a record --
 * types whose Java reference identity is lost in translation, where {@code ==} would
 * become Laurel structural equality. Without this, a generic method laundered the very
 * comparison the restriction exists to reject: measured,
 * {@code precondition(jequals(a, b)); check(eq(a, b))} over two {@code String}s PROVED,
 * although two structurally equal Strings need not be the same object.
 *
 * <p>The decision is made on the BOUND, the only thing known about the instantiation.
 * {@code unbounded} and {@code boundedByObject} are rejected; {@code boundedByPlainClass}
 * is allowed, because a plain class is reference-translated and so pins the runtime class
 * enough to keep {@code ==} a reference comparison.
 */
@JVerifyTest(exitCode = 2)
class TypeVariableEqualityRestricted {

    static class Holder {
        int v;
    }

    @Impure
    static class Tracked {
        int v;
    }

    @Pure
    static <T> boolean unbounded(T a, T b) {
        return a == b;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }

    @Pure
    static <T extends Object> boolean boundedByObject(T a, T b) {
        return a == b;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }

    @Pure
    static <T extends Holder> boolean boundedByPlainClass(T a, T b) {
        return a == b;
    }

    @Pure
    static <T extends Tracked> boolean boundedByImpure(T a, T b) {
        return a == b;
    }
}

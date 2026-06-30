package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * Reference comparisons against the {@code null} literal, and standalone
 * {@code null} values, now translate to Laurel instead of crashing with
 * "Unsupported constant type tag: BOT".
 *
 * <p>An object reference is compared to / assigned the sort's distinguished
 * {@code <sort>$null} value (an uninterpreted element of the otherwise-opaque
 * reference sort). A standalone null picks up its reference sort from the
 * use-site expected type (return type, declared local type, assignment target,
 * or callee parameter type).
 */
@JVerifyTest(methodsVerified = 7, errorCount = 0)
public class NullReferenceTest {

    /** `o != null` in a precondition. */
    int withNonNullPrecondition(Object o) {
        precondition(o != null);
        return 5;
    }

    /** `o == null` as a value, mirrored in the postcondition. */
    boolean isNull(Object o) {
        postcondition((boolean r) -> r == (o == null));
        return o == null;
    }

    /** `return null` — sort taken from the return type. */
    Object returnsNull() {
        return null;
    }

    /** Local initialised to null, then compared (folds to true). */
    boolean localNull() {
        postcondition((boolean r) -> r);
        Object a = null;
        return a == null;
    }

    /** Passing `null` as an argument — sort taken from the parameter type. */
    boolean callWithNull() {
        postcondition((boolean r) -> r);
        return acceptsRef(null);
    }

    @Pure
    boolean acceptsRef(Object o) {
        return o == null;
    }
}

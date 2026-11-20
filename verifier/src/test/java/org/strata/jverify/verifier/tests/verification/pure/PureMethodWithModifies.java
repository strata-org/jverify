package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Impure;
import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.modifies;

/**
 * A user-written {@code modifies(...)} clause on a {@code @Transparent} method is an error.
 *
 * <p>{@code @Transparent} means a TRANSPARENT Laurel body, which cannot carry a frame, so
 * without the error a non-empty {@code modifies} would make the body opaque: the
 * user's clause would silently DEMOTE the method to an opaque procedure -- a
 * different translation instead of a diagnostic.
 *
 * <p>Distinct from a frame the FRONT END synthesizes: a {@code @Transparent} method is
 * allowed to write the heap, and the front end's heap-write
 * fallback gives such a body a wildcard
 * {@code modifies *} regardless of purity. Only the clause the USER wrote is rejected.
 */
@JVerifyTest(exitCode = 2)
public class PureMethodWithModifies {

    @Impure
    static class Box {
        int f;
    }

    @Transparent
    static int pureWithModifies(Box b) {
        modifies(b);
//              ^ error: modifies clauses are not allowed in a transparent method
        return b.f;
    }

    // Opted out, so the rule does not apply -- the same gate (`isNotVerified`) that
    // exempts an opted-out stub from the @Transparent SHAPE rules. Its procedure is dropped
    // rather than reduced to a frameless declaration (see
    // PureSkippedWithFrameIsDropped), so no caller can assume the heap is unchanged.
    @Verify(false)
    @Transparent
    static int optedOutPureWithModifies(Box b) {
        modifies(b);
        return b.f;
    }
}

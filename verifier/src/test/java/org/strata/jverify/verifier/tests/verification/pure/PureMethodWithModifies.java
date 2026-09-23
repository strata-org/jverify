package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Impure;
import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.modifies;

/**
 * A user-written {@code modifies(...)} clause on a {@code @Pure} method is an error.
 *
 * <p>Not inert before this check existed: {@code @Pure} means a TRANSPARENT Laurel
 * body, which cannot carry a frame, so a non-empty {@code modifies} made
 * {@code canStayTransparent} false and the user's clause silently DEMOTED the method
 * to an opaque procedure -- a different translation instead of a diagnostic.
 *
 * <p>Distinct from a frame the FRONT END synthesizes: a {@code @Pure} method is
 * allowed to write the heap, and {@code JavaToLaurelCompiler}'s
 * {@code bodyPotentiallyWritesHeap} fallback gives such a body a wildcard
 * {@code modifies *} regardless of purity. Only the clause the USER wrote is rejected.
 */
@JVerifyTest(exitCode = 2)
public class PureMethodWithModifies {

    @Impure
    static class Box {
        int f;
    }

    @Pure
    static int pureWithModifies(Box b) {
        modifies(b);
//              ^ error: modifies clauses are not allowed in a pure method
        return b.f;
    }

    // Opted out, so the rule does not apply -- the same gate (`isNotVerified`) that
    // exempts an opted-out stub from the @Pure SHAPE rules. Its procedure is dropped
    // rather than reduced to a frameless declaration (see
    // PureSkippedWithFrameIsDropped), so no caller can assume the heap is unchanged.
    @Verify(false)
    @Pure
    static int optedOutPureWithModifies(Box b) {
        modifies(b);
        return b.f;
    }
}

package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * A @Transparent method becomes a Laurel transparent body, which has no slot for a
 * postcondition. Dropping it would let the contradictory postcondition below
 * vacuously "verify", and demoting the method to opaque would silently give a
 * different translation, so the front end rejects the postcondition instead.
 */
@JVerifyTest(exitCode = 2)
class PureLoopWithPostcondition {

    @Transparent
    static boolean loopWithPostcondition(int n) {
        precondition(n >= 0);
        postcondition((boolean r) -> r == true && r == false);
//                   ^ error: postconditions are not allowed in a transparent method
        boolean acc = false;
        for (int i = 0; i < n; i = i + 1) {
            acc = !acc;
        }
        return acc;
    }
}

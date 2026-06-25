package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * A @Pure method whose body contains a loop is emitted uninterpreted by
 * dropping its body. If it also has a postcondition, dropping the body would
 * silently discard that postcondition (it cannot be checked against an
 * uninterpreted function): a contradictory postcondition would vacuously
 * "verify". The translator must refuse this case rather than pass it.
 *
 * <p>The contradictory postcondition below would verify clean (exit 0) if the
 * postcondition were silently dropped; instead the run must report that the
 * combination is not yet supported.
 */
@JVerifyTest(exitCode = 2)
class PureLoopWithPostcondition {

    @Pure
    static boolean loopWithPostcondition(int n) {
//                 ^ error: @Pure function with a loop and a postcondition is not yet supported
        precondition(n >= 0);
        postcondition((boolean r) -> r == true && r == false);
        boolean acc = false;
        for (int i = 0; i < n; i = i + 1) {
            acc = !acc;
        }
        return acc;
    }
}

package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Interpret test for a failing postcondition. The entry calls {@code alwaysZero},
 * which returns 0 but promises {@code res > 0}. Concrete execution reaches the
 * return, evaluates the postcondition, and reports the violation.
 */
@JVerifyTest(
        interpret = true,
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        errorCount = 1)
class InterpretPostconditionFails {
    static int alwaysZero() {
        postcondition((int res) -> res > 0);
//                                 ^^^^^^^ Error: assertion does not hold
        return 0;
    }

    @InterpretEntry
    static void run() {
        int r = alwaysZero();
    }
}

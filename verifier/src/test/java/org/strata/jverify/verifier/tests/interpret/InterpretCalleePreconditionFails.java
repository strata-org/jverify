package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Cross-procedure interpret test: the @InterpretEntry method calls a helper
 * whose precondition does not hold at runtime, and the interpreter reports the
 * violation. This exercises that contracts on callees are evaluated (unlike
 * preconditions on the entry itself, which are not injected as runtime guards).
 */
@JVerifyTest(
        interpret = true,
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        errorCount = 1)
class InterpretCalleePreconditionFails {
    static int addOne(int x) {
        precondition(x > 0);
        return x + 1;
    }

    @InterpretEntry
    static void run() {
        int r = addOne(0);
//      ^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}

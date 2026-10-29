package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder follows the sign of the
// dividend, the identity (a/b)*b+(a%b)==a, and floating-point remainder.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.RemainderOperator_9#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class RemainderOperator_9 {
    static void run() {

        // floating-point remainder
        double d = 7.5 % 2.0;
        check(d == 1.5);
    }
}

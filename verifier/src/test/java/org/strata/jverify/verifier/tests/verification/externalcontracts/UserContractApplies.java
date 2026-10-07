// A user contract directory's @Contract(Math.class) gives Math.abs a
// specification: callers prove what it states, and only that.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, contractSourcePath = {"usercontracts/applies"}, exitCode = 4, methodsVerified = 2, methodsInvalid = 2, errorCount = 2)
class UserContractApplies {
    static int nonNegative(int x) {
        precondition(x > Integer.MIN_VALUE);
        int y = Math.abs(x);
        check(y >= 0);
        check(y == x || y == -x);
        return y;
    }

    static int notStated(int x) {
        precondition(x > Integer.MIN_VALUE);
        int y = Math.abs(x);
        check(y > 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
        return y;
    }

    static int violatesThePrecondition() {
        return Math.abs(Integer.MIN_VALUE);
//             ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
    }
}

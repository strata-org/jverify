// With the builtin contracts, Integer.valueOf and intValue apply their
// contracts, so the round trip verifies.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, useBuiltinContracts = true, continueOnErrors = true, exitCode = 0, methodsInvalid = 0, errorCount = 0, methodsVerified = 2, methodsSkipped = 0)
class BoxedIntegerBuiltin {
    static boolean v(int i) {
        Integer b = Integer.valueOf(i);
        return b.intValue() == i;
    }
}

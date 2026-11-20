// A postcondition on a @Transparent user contract member is an error, reported
// at the postcondition in the contract file; the member's callers are not verified.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/transparentpostcondition"}, continueOnErrors = true, exitCode = 0, methodsVerified = 2, errorCount = 2, methodsSkipped = 1)
class UserContractTransparentPostcondition {
    static void caller() {
//                       ^ error: call to 'max', which could not be translated
        int r = Math.max(2, 3);
        check(r >= 3);
    }

    static void unrelated(int x) {
        check(x == x);
    }
}

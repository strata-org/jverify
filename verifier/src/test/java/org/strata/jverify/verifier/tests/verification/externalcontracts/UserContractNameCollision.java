// Two user contract classes sharing a simple name end the run without a
// verdict, reported at the second one.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(contractSourcePath = {"usercontracts/namecollision"}, exitCode = 2)
class UserContractNameCollision {
    static int one() {
        return 1;
    }
}

// A user @Contract class with neither a target nor a supertype ends the run
// without a verdict.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(contractSourcePath = {"usercontracts/missingtarget"}, exitCode = 2)
class UserContractMissingTarget {
    static int one() {
        return 1;
    }
}

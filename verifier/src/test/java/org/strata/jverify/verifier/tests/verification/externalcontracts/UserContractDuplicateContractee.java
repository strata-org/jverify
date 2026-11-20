// Two user contract classes for the same contractee end the run without a
// verdict, reported at the second one.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(contractSourcePath = {"usercontracts/duplicatecontractee"}, exitCode = 2)
class UserContractDuplicateContractee {
    static int one() {
        return 1;
    }
}

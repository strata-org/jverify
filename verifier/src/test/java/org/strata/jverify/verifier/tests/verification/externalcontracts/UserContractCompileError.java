// A javac error in a user contract directory ends the run without a verdict,
// reported in the contract file.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(contractSourcePath = {"usercontracts/compileerror"}, exitCode = 2)
class UserContractCompileError {
    static int one() {
        return 1;
    }
}

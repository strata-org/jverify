// A user contract class for another class that has the simple name of a
// shipped wrapper contract class does not replace that contract: the run ends
// without a verdict, with the clash reported at the user class.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(contractSourcePath = {"usercontracts/wrappernameclash"}, exitCode = 2)
class UserContractWrapperNameClash {
    static int one() {
        return 1;
    }
}

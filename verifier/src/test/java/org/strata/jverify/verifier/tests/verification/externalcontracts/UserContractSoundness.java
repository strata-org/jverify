// Which user contract bodies a caller may assume: a verified @Pure member's
// body is its definition; an opted-out member's body is not assumed; an impure
// member with a body is refused, and so is a body returning a range-constrained
// value (an int), so their callers are not verified.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/soundness"}, continueOnErrors = true, exitCode = 4, methodsVerified = 2, methodsInvalid = 1, errorCount = 1, methodsSkipped = 2)
class UserContractSoundness {
    static void verifiedPureBodyIsTheDefinition() {
        check(Boolean.logicalAnd(false, false));
    }

    static void optedOutBodyIsNotAssumed() {
        check(Math.max(1, 2) == 7);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void impureBodyRefusesTheCaller() {
//                                           ^ error: call to 'abs', which could not be translated: a contract for an impure method may only call super or this, or throw a ContractException in its body
        check(Math.abs(3) == 7);
    }

    static void constrainedResultRefusesTheCaller() {
//                                                  ^ error: call to 'min', which could not be translated: a contract body that returns 'int' is not yet supported: Strata cannot check the result's range
        check(Math.min(1, 2) == 7);
    }
}

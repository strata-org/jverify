// A contract constructor that has both a body and postconditions is refused
// also when it states modifies(this): its callers would assume the postcondition
// and what the body assigns, which here contradict, and prove anything.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/constructorpostconditionmodifies"}, continueOnErrors = true, methodsSkipped = 1)
class UserContractConstructorPostconditionModifies {
    static void anythingAfterIt() {
//                                ^ error: call to '$init', which could not be translated: a contract constructor with both a body and postconditions is not yet supported: the body is not checked against the postconditions
        AtomicInteger a = new AtomicInteger(5);
        check(1 == 2);
    }
}

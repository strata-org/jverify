// A contract constructor that has both a body and postconditions is refused,
// and so are its callers: the body is what a caller would see, and it is not
// checked against the postconditions.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/constructorpostcondition"}, continueOnErrors = true, methodsSkipped = 2)
class UserContractConstructorPostcondition {
    static void asTheBodySays() {
//                              ^ error: call to '$init', which could not be translated: a contract constructor with both a body and postconditions is not yet supported: the body is not checked against the postconditions
        AtomicInteger a = new AtomicInteger(5);
        check(a.get() == 0);
    }

    static void asThePostconditionSays() {
//                                       ^ error: call to '$init', which could not be translated: a contract constructor with both a body and postconditions is not yet supported: the body is not checked against the postconditions
        AtomicInteger a = new AtomicInteger(5);
        check(a.get() == 5);
    }
}

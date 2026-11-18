// A type test or a cast to a class that a user contract describes with fields
// is refused: a program class that extends the described class is not known
// to be of the contract's sort, so the test would be decided wrongly for it.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/contractsubclass"}, continueOnErrors = true, methodsSkipped = 2)
class UserContractSubclassTypeTest {
    static class P extends java.awt.Point {
        P() {
            super(1, 2);
        }
    }

    static void typeTest() {
//                         ^ error: instanceof on opaque reference types is not yet supported
        Object o = new P();
        check(!(o instanceof java.awt.Point));
    }

    static void cast() {
//                     ^ error: Cast to Point is not yet supported
        Object o = new P();
        java.awt.Point p = (java.awt.Point) o;
    }
}

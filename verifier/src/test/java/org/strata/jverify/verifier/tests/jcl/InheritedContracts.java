// Integer declares no equals contract, so a.equals(b) binds to
// object-contract.java's Object.equals and the call verifies.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(useBuiltinContracts = true, methodsVerified = 2, methodsSkipped = 0)
class InheritedContracts {
    static boolean sameValue(Integer a, Integer b) {
        return a.equals(b);
    }
}

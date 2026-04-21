package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testing.ContainsFoo;
import org.strata.jverify.testing.InheritsFoo;

import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(methodsVerified = 1, errorCount = 0)
public class RefineContractOfInheritedMethod {
    @Contract(ContainsFoo.class)
    static class ContainsFooContract {
        public int foo() {
            postcondition((int r) -> r > 0);
            throw new ContractException();
        }
    }
    
    @Contract(InheritsFoo.class)
    static class InheritsFooContract {
        public int foo() {
            postcondition((int r) -> r > 1);
            throw new ContractException();
        }
    }
}

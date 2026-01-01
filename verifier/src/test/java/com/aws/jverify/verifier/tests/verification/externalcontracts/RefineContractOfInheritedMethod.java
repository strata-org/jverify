package com.aws.jverify.verifier.tests.verification.externalcontracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.testing.ContainsFoo;
import com.aws.jverify.testing.InheritsFoo;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(javaVerified = 3, javaErrors = 0)
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

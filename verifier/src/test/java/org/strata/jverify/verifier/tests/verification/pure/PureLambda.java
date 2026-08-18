package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

@JVerifyTest(methodsVerified = 2, errorCount = 0)
public class PureLambda {

    void lambdaBecomesPureWhenSamIs() {
//       ^ warning: instance method 'lambdaBecomesPureWhenSamIs' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        takesFunction(i -> true);
    }
    
    void takesFunction(IntPredicate f) {
        
    }
    
    @Contract
    static class IntPredicateContract implements IntPredicate {

        @Override
        public boolean test(int value) {
            throw new ContractException();
        }
    }
}

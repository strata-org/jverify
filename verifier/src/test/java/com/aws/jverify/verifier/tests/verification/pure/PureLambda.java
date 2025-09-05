package com.aws.jverify.verifier.tests.verification.pure;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

@JVerifyTest(dafnyVerified = 17, dafnyErrors = 0)
public class PureLambda {

    void lambdaBecomesPureWhenSamIs() {
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

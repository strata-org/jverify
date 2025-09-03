package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 9, dafnyErrors = 0)
public class PureMethodVerification {

    @Pure
    int pureMethodWithAssignments() {
        var x = 3;
        var y = 2;
        return x + y;
    }

    @Pure
    int pureMethodWithEndingIf(int x) {
        var y = 2;
        if (x > 1) {
            var z = 3;
            return y + z;
        } else {
            return x + y;
        }
    }
    
    interface Base {
        @Pure
        int pureIsInherited();
        
        @Contract
        class ContractClass implements Base {

            @Pure
            @Override
            public int pureIsInherited() {
                throw new ContractException();
            }
        }
            
    }
    
    static class Extender implements Base {
        public int pureIsInherited() {
            return 4;
        }
        
        @Pure
        int callsPure() {
            return pureIsInherited();
        }
    }
}

package com.aws.jverify.verifier.tests.verification.pure;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 8, failedAssertions = 1)
public class PureMethodVerification {

    @Verify(false)
    @Pure
    public int unverifiedWithContractException() {
        throw new ContractException();
    }
    
    @Pure
    int pureMethodWithAssumeAndCheck(int x) {
        assume(x > 3);
        check(x > 2);
        check(x > 4);
//      ^^^^^^^^^^^^ Error: assertion could not be proved
        return x;
    }
    
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
    
    @Pure
    int pureMethodWithEarlyExits(int x) {
        if (x > 10) {
            return 3;
        }
        
        if (x > 5) {
            return 5;
        }
        return 7;
    }
    
    @SuppressWarnings("UnnecessaryLocalVariable")
    @Pure
    int nesting(int a) {
        precondition(a > 0 && a < 100);
        var b = a + 2;
        if (b > 2) {
            var c = b + 3;
            if (c > 3) {
                return c + 4;
            }
            var d = c + 5;
            return d + 6;
        }
        var e = b + 1;
        return e;
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

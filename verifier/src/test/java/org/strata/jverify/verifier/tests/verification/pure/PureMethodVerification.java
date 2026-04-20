package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 8, errorCount = 1)
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
//      ^^^^^^^^^^^^ Error: assertion does not hold
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

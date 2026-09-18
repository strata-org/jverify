package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_INSTANCE_METHODS}, exitCode = 4, methodsVerified = 1, errorCount = 1)
public class PureUnverifiedMethod {
    
    void pureUnverifiedBodyIsNotVisible() {
        var x = returnsTwo();
        check(x == 2);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
    
    @Verify(false)
    @Pure
    int returnsTwo() {
       return 2; 
    }
}

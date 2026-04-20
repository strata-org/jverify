package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 1, errorCount = 1)
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

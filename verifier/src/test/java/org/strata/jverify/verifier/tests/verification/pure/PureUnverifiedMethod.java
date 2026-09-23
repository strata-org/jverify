package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

// A `@Verify(false) @Pure` method keeps an opaque DECLARATION, so the call
// resolves and the caller is verifiable -- it just learns nothing about the
// result, which is what this fixture pins.
@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 1)
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

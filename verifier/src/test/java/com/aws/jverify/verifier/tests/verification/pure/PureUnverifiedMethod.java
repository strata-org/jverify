package com.aws.jverify.verifier.tests.verification.pure;

import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 3, dafnyErrors = 1)
public class PureUnverifiedMethod {
    
    void pureUnverifiedBodyIsNotVisible() {
        var x = returnsTwo();
        check(x == 2);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }
    
    @Verify(false)
    @Pure
    int returnsTwo() {
       return 2; 
    }
}

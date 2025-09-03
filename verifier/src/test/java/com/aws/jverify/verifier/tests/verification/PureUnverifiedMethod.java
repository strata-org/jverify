package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 4, dafnyErrors = 1)
public class PureUnverifiedMethod {
    
    void pureUnverifiedBodyIsNotVisible() {
        var x = returnsTwo();
        check(x == 2);
//      ^^^^^^^^^^^^^ Error: assertion might not hold
    }
    
    @Verify(false)
    @Pure
    int returnsTwo() {
       return 2; 
    }
}

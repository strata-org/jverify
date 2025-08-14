package com.aws.jverify.verifier.tests.verification;


import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 7, dafnyErrors = 0, verifyPrintedDafny = true)
public class MissingContracts {
    interface MissingContract {
        int assumedPure();
        void cantBePure();
    }
    
    @Pure
    int pureUser(MissingContract missingContract) {
        return missingContract.assumedPure();
    }
}

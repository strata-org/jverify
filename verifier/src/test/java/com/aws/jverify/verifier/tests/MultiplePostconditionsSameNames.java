package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class MultiplePostconditionsSameNames {
    
    // This should work - same parameter name
    public static int validMultiplePostconditions() {
        postcondition((Integer r) -> r > 0);
        postcondition((Integer r) -> r < 100);
        return 42;
    }
}

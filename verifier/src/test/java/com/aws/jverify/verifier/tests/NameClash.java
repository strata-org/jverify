package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 0, dafnyVerified = 4, dafnyErrors = 0)
public class NameClash {
    public int m() {
        int r;
        r = 0;
        return r;
    }
}

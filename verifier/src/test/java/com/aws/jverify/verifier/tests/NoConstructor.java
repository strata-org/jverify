package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 3, dafnyErrors = 0)
class NoConstructor {
    public int f;
}

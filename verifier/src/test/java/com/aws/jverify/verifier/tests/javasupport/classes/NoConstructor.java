package com.aws.jverify.verifier.tests.javasupport.classes;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
class NoConstructor {
    public int f;
}

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 1, dafnyErrors = 0)
class NoConstructor {
    public int f;
}
package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 6, dafnyErrors = 1)
class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}
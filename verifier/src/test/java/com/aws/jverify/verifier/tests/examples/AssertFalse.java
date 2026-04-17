package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 1)
class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}

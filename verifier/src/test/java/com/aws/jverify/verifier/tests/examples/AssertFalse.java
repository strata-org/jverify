package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Dafny;
import static com.aws.jverify.verifier.Backend.Laurel;

@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 1, BACKENDS = { Laurel })
class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}

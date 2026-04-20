package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 1)
class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}

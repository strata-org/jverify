package org.strata.jverify.verifier.tests.javasupport;

import static org.strata.jverify.JVerify.check;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 3, errorCount = 0)
class FieldReadInACondition {

    int x;

    void ifField() {
        if (x > 5) {
            check(x > 5);
        }
    }

    void whileField() {
        while (x > 5) {
            check(x > 5);
            x = x - 1;
        }
    }
}

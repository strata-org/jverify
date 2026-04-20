package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class ForLoop {
    static void simpleFor() {
        int sum = 0;
        for (int i = 0; i < 3; i = i + 1) {
            invariant(0 <= sum && sum <= 3);
            invariant(0 <= i && i <= 3);
            invariant(sum == i);
            sum = sum + 1;
        }
        check(sum == 3);
    }
}

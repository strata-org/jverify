package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 2)
class StrataBasic {
    static void arithmetic() {
        int x = 5;
        int y = x * 2 - 3;
        check(y == 7);
        check(y == 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void controlFlow(int x) {
        precondition(x >= 0);
        int r;
        if (x > 10) {
            r = x - 10;
        } else {
            r = x;
        }
        check(r >= 0);
        check(r > 10);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void assumeTest(int x) {
        assume(x > 100);
        check(x > 0);
    }
}

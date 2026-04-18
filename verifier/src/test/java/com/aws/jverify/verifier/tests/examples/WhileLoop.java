package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class WhileLoop {
    static void countDown() {
        int i = 3;
        while (i > 0) {
            invariant(0 <= i && i <= 3);
            i = i - 1;
        }
        check(i == 0);
    }

    static int sumTo(int n) {
        precondition(0 <= n && n <= 65535);
        postcondition((int res) -> res == n * (n + 1) / 2);
        int i = 0;
        int s = 0;
        while (i < n) {
            invariant(0 <= i && i <= n);
            invariant(s == i * (i + 1) / 2);
            i = i + 1;
            s = s + i;
        }
        return s;
    }
}

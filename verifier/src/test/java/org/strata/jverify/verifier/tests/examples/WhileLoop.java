package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

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
        // 46340 = floor(sqrt(Integer.MAX_VALUE)): n * (n + 1) in the spec is
        // overflow-checked like any other arithmetic, and at the old bound
        // of 65535 the product really exceeds Integer.MAX_VALUE.
        precondition(0 <= n && n <= 46340);
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

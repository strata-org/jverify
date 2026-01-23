package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 2, errorCount = 0, BACKENDS = {Strata})
class WhileBasic {
    static int sum(int n) {
        precondition(n >= 0);
        postcondition((int res) -> res == n * (n + 1) / 2);

        int i = 0;
        int s = 0;
        while (i < n) {
            invariant(i >= 0 && i <= n);
            invariant(s == i * (i + 1) / 2);
            i = i + 1;
            s = s + i;
        }
        return s;
    }
}

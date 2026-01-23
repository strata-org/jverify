package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 2, errorCount = 0, BACKENDS = {Strata})
class BoundedInts {
    static int addPositive(int x, int y) {
        precondition(x >= 0 && x <= 1000);
        precondition(y >= 0 && y <= 1000);
        postcondition((int res) -> res == x + y);
        return x + y;
    }
}

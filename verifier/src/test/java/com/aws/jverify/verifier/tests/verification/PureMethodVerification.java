package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 5, dafnyErrors = 0)
public class PureMethodVerification {

    @Pure
    int pureMethodWithAssignments() {
        var x = 3;
        var y = 2;
        return x + y;
    }

    @Pure
    int pureMethodWithEndingIf(int x) {
        var y = 2;
        if (x > 1) {
            var z = 3;
            return y + z;
        } else {
            return x + y;
        }
    }
}

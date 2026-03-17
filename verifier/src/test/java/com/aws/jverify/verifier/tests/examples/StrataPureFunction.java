package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataPureFunction {
    @Pure
    static boolean isPositive(boolean x) {
        return x;
    }

    static void callerCanSeeReturnValue() {
        boolean r = isPositive(true);
        check(r);
    }
}

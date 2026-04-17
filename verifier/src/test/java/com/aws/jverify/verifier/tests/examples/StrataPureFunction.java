package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

// @Pure with int return type is not yet supported in Strata (constrained return types on functions).
// Testing with boolean return type only.
@JVerifyTest(exitCode = 0)
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

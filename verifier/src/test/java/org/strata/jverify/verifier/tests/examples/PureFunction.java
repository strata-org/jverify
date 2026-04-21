package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// @Pure with int return type is not yet supported in Strata (constrained return types on functions).
// Testing with boolean return type only.
@JVerifyTest(exitCode = 0)
class PureFunction {
    @Pure
    static boolean isPositive(boolean x) {
        return x;
    }

    static void callerCanSeeReturnValue() {
        boolean r = isPositive(true);
        check(r);
    }
}

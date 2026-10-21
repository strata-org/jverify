package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class McCarthy91 {
    static @Unbounded int mc91(@Unbounded int n) {
        decreases(101 - n);
        postcondition((int res) -> res == (n > 100 ? n - 10 : 91));

        if (n > 100) {
            return n - 10;
        }
        return mc91(mc91(n + 11));
    }
}

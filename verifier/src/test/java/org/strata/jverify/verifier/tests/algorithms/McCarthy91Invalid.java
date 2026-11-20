package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_DECREASES}, exitCode = 4)
class McCarthy91Invalid {
    static @Unbounded int mc91(@Unbounded int n) {
        decreases(101 - n);
        postcondition((int res) -> res == (n > 100 ? n - 10 : 91));

        if (n > 100) {
            return n - 10;
        }
        return mc91(mc91(n + 10));
//             ^^^^^^^^^^^^^^^^^^ Error: decreases clause could not be proved to decrease
    }
}

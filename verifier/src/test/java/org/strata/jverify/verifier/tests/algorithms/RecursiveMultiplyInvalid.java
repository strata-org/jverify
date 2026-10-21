package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 2)
class RecursiveMultiplyInvalid {
    static int mult(@Nat int a, @Nat int b) {
        precondition(0 <= a && a <= 1000);
        precondition(0 <= b && b <= 1000);
        decreases(a);
        postcondition((int res) -> res == a * b);
//                                 ^^^^^^^^^^^^ Error: postcondition does not hold

        if (a == 0) {
            return 0;
        }
        return b + mult(a - 1, b - 1);
//                 ^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
    }
}

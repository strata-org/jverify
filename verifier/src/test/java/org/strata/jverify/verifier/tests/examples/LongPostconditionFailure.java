package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 2)
class LongPostconditionFailure {
    static long twice(long x) {
        precondition(-1000L <= x && x <= 1000L);
        postcondition((long r) -> r == 2 * x);
//                                ^^^^^^^^^^ Error: postcondition does not hold
        return 2 * x + 1;
    }

    static void everyLongIsSmall() {
        check(forall((long n) -> n < 4294967296L));
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}

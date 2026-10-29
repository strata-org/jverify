package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: Integer.MIN_VALUE / -1 overflows silently to
// Integer.MIN_VALUE; no exception is thrown.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegerOperations_MinDivMinusOne#run")
class IntegerOperations_MinDivMinusOne {
    static void run() {

        int r = Integer.MIN_VALUE / -1;
        check(r == Integer.MIN_VALUE);
    }
}

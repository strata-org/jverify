package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: unary negation of Integer.MIN_VALUE
// overflows silently back to Integer.MIN_VALUE.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegerOperations_UnaryNegMin#run")
class IntegerOperations_UnaryNegMin {
    static void run() {

        int r = -Integer.MIN_VALUE;
        check(r == Integer.MIN_VALUE);
    }
}

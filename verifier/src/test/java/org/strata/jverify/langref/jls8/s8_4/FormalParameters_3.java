package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.1 -- Formal Parameters: ordinary parameters, final parameters,
// and a variable arity (varargs) parameter that receives several arguments.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.FormalParameters_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FormalParameters_3 {
    // Two ordinary formal parameters, the second declared final.
    static int add(int a, final int b) {
        return a + b;
    }

    // A variable arity parameter: int... collapses trailing args into an array.
    static int sum(int... xs) {
        int total = 0;
        for (int x : xs) {
            total += x;
        }
        return total;
    }

    static void run() {

        check(sum(7) == 7);
    }
}

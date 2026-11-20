package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.1 -- Formal Parameters: constructor with multiple formal parameters, including a final parameter and a varargs parameter.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.FormalParameters_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FormalParameters_1 {
    final int base;
    final int total;

    FormalParameters_1(final int base, int... rest) {
        int sum = base;
        for (int r : rest) {
            sum += r;
        }
        this.base = base;
        this.total = sum;
    }

    static void run() {

        FormalParameters_1 f = new FormalParameters_1(10, 1, 2, 3);
        check(f.base == 10);
    }
}

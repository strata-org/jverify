package org.strata.jverify.langref.jls14.s14_21;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.21 -- The yield Statement: yields a value from a switch expression block.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_21.YieldStatement_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class YieldStatement_1 {
    static void run() {

        int day = 3;
        int letters = switch (day) {
            case 1 -> 6;
            case 2 -> {
                int base = 5;
                yield base + 2;
            }
            case 3 -> {
                int n = 0;
                for (int i = 0; i < 4; i++) {
                    n += i;
                }
                yield n; // 0+1+2+3 == 6
            }
            default -> -1;
        };
        check(letters == 6);
    }
}

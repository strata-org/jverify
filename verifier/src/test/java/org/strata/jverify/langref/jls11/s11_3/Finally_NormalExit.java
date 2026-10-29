package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- finally runs on normal completion of the try block.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.Finally_NormalExit#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class Finally_NormalExit {
    static void run() {
        int[] side = new int[1];
        try {
            side[0] = 1;
        } finally {
            side[0] += 10;
        }
        check(side[0] == 11);
    }
}

package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.3 -- switch with colon labels (fallthrough): 'case L:' falls through to the
// statements of subsequent labels until a break (or end of block) is reached.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchColonFallthrough_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchColonFallthrough_2 {
    static void run() {

        // entering at case 2 only falls through 2 and 3.
        int sum2 = 0;
        int start2 = 2;
        switch (start2) {
            case 1:
                sum2 += 1;
            case 2:
                sum2 += 2;
            case 3:
                sum2 += 3;
                break;
            default:
                sum2 += 100;
        }
        check(sum2 == 5);
    }
}

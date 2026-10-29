package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.3 -- switch with colon labels (fallthrough): 'case L:' falls through to the
// statements of subsequent labels until a break (or end of block) is reached.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchColonFallthrough_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchColonFallthrough_1 {
    static void run() {

        // entering at case 1 falls through cases 2 and 3, accumulating each.
        int sum = 0;
        int start = 1;
        switch (start) {
            case 1:
                sum += 1;
                // fall through
            case 2:
                sum += 2;
                // fall through
            case 3:
                sum += 3;
                break;
            default:
                sum += 100;
        }
        check(sum == 6);
    }
}

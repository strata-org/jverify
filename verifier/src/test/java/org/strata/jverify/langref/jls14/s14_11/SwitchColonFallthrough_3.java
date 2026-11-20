package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.3 -- switch with colon labels (fallthrough): 'case L:' falls through to the
// statements of subsequent labels until a break (or end of block) is reached.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchColonFallthrough_3#run")
class SwitchColonFallthrough_3 {
    static void run() {

        // break terminates the switch, preventing fallthrough into later labels.
        int v = 1;
        int out = 0;
        switch (v) {
            case 1:
                out = 11;
                break;
            case 2:
                out = 22;
                break;
            default:
                out = 99;
        }
        check(out == 11);
    }
}

package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.1 -- Switch Blocks: a switch block groups switch labels (case/default) over a
// selector expression; each label denotes a value, default matches anything unmatched.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchBlocks_3#run")
class SwitchBlocks_3 {
    static void run() {

        // multiple constants sharing one labeled group (comma-separated case)
        int day = 6;
        boolean weekend = false;
        switch (day) {
            case 6, 7:
                weekend = true;
                break;
            default:
                weekend = false;
                break;
        }
        check(weekend);
    }
}

package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.1 -- Switch Blocks: a switch block groups switch labels (case/default) over a
// selector expression; each label denotes a value, default matches anything unmatched.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchBlocks_1#run")
class SwitchBlocks_1 {
    static void run() {

        int selector = 2;
        int result = -1;
        switch (selector) {
            case 1:
                result = 10;
                break;
            case 2:
                result = 20;
                break;
            case 3:
                result = 30;
                break;
            default:
                result = 0;
                break;
        }
        check(result == 20);
    }
}

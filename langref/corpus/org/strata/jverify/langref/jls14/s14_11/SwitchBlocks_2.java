package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11.1 -- Switch Blocks: a switch block groups switch labels (case/default) over a
// selector expression; each label denotes a value, default matches anything unmatched.
class SwitchBlocks_2 {
    static void run() {

        // default matches a value with no explicit case
        int other = 99;
        int r2 = -1;
        switch (other) {
            case 1:
                r2 = 1;
                break;
            default:
                r2 = 0;
                break;
        }
        check(r2 == 0);
    }
}

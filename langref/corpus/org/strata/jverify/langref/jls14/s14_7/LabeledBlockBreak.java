package org.strata.jverify.langref.jls14.s14_7;

import static org.strata.jverify.JVerify.*;

// JLS 14.7 -- Labeled Statements: a label may prefix any statement (here a plain block),
// and break with that label transfers control to just after the labeled block.
class LabeledBlockBreak {
    static void run() {
        int x = 0;
        block:
        {
            x = 1;
            if (x == 1) {
                break block;
            }
            x = 99; // unreachable due to break
        }
        check(x == 1);
    }
}

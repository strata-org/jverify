package org.strata.jverify.langref.jls14.s14_15;

import static org.strata.jverify.JVerify.*;

// JLS 14.15 -- The break Statement: an unlabeled break transfers control out of
// the immediately enclosing switch/loop; a labeled break (break Identifier)
// transfers control out of the enclosing labeled statement, allowing exit from
// outer loops or arbitrary labeled blocks.
class BreakStatement_1 {
    static void run() {

        // Unlabeled break exits the immediately enclosing loop.
        int firstEven = -1;
        for (int i = 1; i <= 10; i++) {
            if (i % 2 == 0) {
                firstEven = i;
                break;
            }
        }
        check(firstEven == 2);
    }
}

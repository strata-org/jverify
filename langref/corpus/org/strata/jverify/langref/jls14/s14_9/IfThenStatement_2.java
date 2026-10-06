package org.strata.jverify.langref.jls14.s14_9;

import static org.strata.jverify.JVerify.*;

// JLS 14.9.1 -- The if-then Statement: 'if (cond) stmt' executes the body only when the condition is true.
class IfThenStatement_2 {
    static void run() {

        int y = 0;
        if (1 > 2) {
            y = 99;
        }
        check(y == 0);
    }
}

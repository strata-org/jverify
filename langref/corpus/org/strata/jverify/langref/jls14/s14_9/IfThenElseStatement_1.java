package org.strata.jverify.langref.jls14.s14_9;

import static org.strata.jverify.JVerify.*;

// JLS 14.9.2 -- The if-then-else Statement: 'if (cond) stmt else stmt' chooses one branch; else binds to nearest if.
class IfThenElseStatement_1 {
    static void run() {

        int x;
        if (3 > 2) {
            x = 1;
        } else {
            x = 2;
        }
        check(x == 1);
    }
}

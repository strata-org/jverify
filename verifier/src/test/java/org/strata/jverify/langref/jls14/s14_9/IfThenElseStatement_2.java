package org.strata.jverify.langref.jls14.s14_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.9.2 -- The if-then-else Statement: 'if (cond) stmt else stmt' chooses one branch; else binds to nearest if.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_9.IfThenElseStatement_2#run")
class IfThenElseStatement_2 {
    static void run() {

        int y;
        if (1 > 2) {
            y = 1;
        } else {
            y = 2;
        }
        check(y == 2);
    }
}

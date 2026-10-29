package org.strata.jverify.langref.jls14.s14_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.9.1 -- The if-then Statement: 'if (cond) stmt' executes the body only when the condition is true.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_9.IfThenStatement_1#run")
class IfThenStatement_1 {
    static void run() {

        int x = 0;
        if (3 > 2) {
            x = 5;
        }
        check(x == 5);
    }
}

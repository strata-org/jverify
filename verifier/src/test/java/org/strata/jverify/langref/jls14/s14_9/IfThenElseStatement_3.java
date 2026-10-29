package org.strata.jverify.langref.jls14.s14_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.9.2 -- The if-then-else Statement: 'if (cond) stmt else stmt' chooses one branch; else binds to nearest if.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_9.IfThenElseStatement_3#run")
class IfThenElseStatement_3 {
    static void run() {

        // Dangling-else: the else binds to the nearest preceding if (the inner one).
        int z = 0;
        if (true)
            if (false)
                z = 10;
            else
                z = 20;
        check(z == 20);
    }
}

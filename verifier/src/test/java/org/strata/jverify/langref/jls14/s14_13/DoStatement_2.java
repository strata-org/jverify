package org.strata.jverify.langref.jls14.s14_13;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.13 -- The do Statement: the body executes at least once, then the
// condition is evaluated; if true the loop repeats. Also exercises break and
// continue inside a do-while.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_13.DoStatement_2#run")
class DoStatement_2 {
    static void run() {
        // 1 + 2 + 3 + 4

        // Body executes at least once even when condition is initially false.
        int count = 0;
        do {
            count++;
        } while (false);
        check(count == 1);
    }
}

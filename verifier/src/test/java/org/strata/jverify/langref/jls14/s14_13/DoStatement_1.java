package org.strata.jverify.langref.jls14.s14_13;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.13 -- The do Statement: the body executes at least once, then the
// condition is evaluated; if true the loop repeats. Also exercises break and
// continue inside a do-while.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_13.DoStatement_1#run")
class DoStatement_1 {
    static void run() {

        // Basic do-while: body runs, then condition checked.
        int sum = 0;
        int i = 1;
        do {
            sum += i;
            i++;
        } while (i <= 4);
        check(sum == 10);
    }
}

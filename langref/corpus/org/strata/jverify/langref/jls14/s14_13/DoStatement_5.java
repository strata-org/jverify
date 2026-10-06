package org.strata.jverify.langref.jls14.s14_13;

import static org.strata.jverify.JVerify.*;

// JLS 14.13 -- The do Statement: the body executes at least once, then the
// condition is evaluated; if true the loop repeats. Also exercises break and
// continue inside a do-while.
class DoStatement_5 {
    static void run() {

        // Basic do-while: body runs, then condition checked.
        int sum = 0;
        int i = 1;
        do {
            sum += i;
            i++;
        } while (i <= 4);  // 3 skipped, stop at 6

        check(sum == 10);
    }
}

package org.strata.jverify.langref.jls14.s14_13;

import static org.strata.jverify.JVerify.*;

// JLS 14.13 -- The do Statement: the body executes at least once, then the
// condition is evaluated; if true the loop repeats. Also exercises break and
// continue inside a do-while.
class DoStatement_4 {
    static void run() {

        // continue jumps to the condition; break exits the loop.
        int collected = 0;
        int j = 0;
        do {
            j++;
            if (j == 3) {
                continue;        // skip adding 3, then re-test condition
            }
            if (j == 6) {
                break;           // leave the loop entirely
            }
            collected += j;
        } while (j < 10);
        check(collected == 1 + 2 + 4 + 5);
    }
}

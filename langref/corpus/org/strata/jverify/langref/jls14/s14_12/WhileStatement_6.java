package org.strata.jverify.langref.jls14.s14_12;

import static org.strata.jverify.JVerify.*;

// JLS 14.12 -- The while Statement: evaluates a boolean condition before each
// iteration and executes the body while it is true; covers a normal loop, a
// loop whose condition is initially false (body never runs), and abrupt
// completion of the body via break and continue.
class WhileStatement_6 {
    static void run() {
 // odd values 1,3,5

        // Single-statement body (no block).
        int down = 5;
        while (down > 0)
            down--;
        check(down == 0);
    }
}

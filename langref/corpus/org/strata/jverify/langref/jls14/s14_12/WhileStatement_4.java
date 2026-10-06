package org.strata.jverify.langref.jls14.s14_12;

import static org.strata.jverify.JVerify.*;

// JLS 14.12 -- The while Statement: evaluates a boolean condition before each
// iteration and executes the body while it is true; covers a normal loop, a
// loop whose condition is initially false (body never runs), and abrupt
// completion of the body via break and continue.
class WhileStatement_4 {
    static void run() {

        // break causes abrupt, normal completion of the while statement.
        int j = 0;
        while (true) {
            j++;
            if (j == 3) {
                break;
            }
        }
        check(j == 3);
    }
}

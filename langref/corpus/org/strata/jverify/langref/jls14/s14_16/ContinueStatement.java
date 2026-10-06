package org.strata.jverify.langref.jls14.s14_16;

import static org.strata.jverify.JVerify.*;

// JLS 14.16 -- The continue Statement: unlabeled continue in a for loop
// skips the rest of the loop body and proceeds to the update/condition.
class ContinueStatement {
    static void run() {
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                continue;          // skip even numbers
            }
            sum += i;              // adds only 1 + 3 = 4
        }
        check(sum == 4);
    }
}

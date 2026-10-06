package org.strata.jverify.langref.jls14.s14_14;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.1 -- The basic for Statement: ForInit; Expression; ForUpdate with a classic counting loop.
class BasicForStatement {
    static void run() {
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            sum += i;
        }
        check(sum == 15);
    }
}

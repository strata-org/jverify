package org.strata.jverify.langref.jls14.s14_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.1 -- The basic for Statement: ForInit; Expression; ForUpdate with a classic counting loop.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_14.BasicForStatement#run")
class BasicForStatement {
    static void run() {
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            sum += i;
        }
        check(sum == 15);
    }
}

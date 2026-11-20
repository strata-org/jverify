package org.strata.jverify.langref.jls14.s14_16;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.16 -- The continue Statement: unlabeled continue in a while loop.
// The continue branches to the loop's controlling expression.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_16.ContinueWhile#run")
class ContinueWhile {
    static void run() {
        int i = 0;
        int count = 0;
        while (i < 6) {
            i++;
            if (i <= 3) {
                continue;          // does not increment count for 1,2,3
            }
            count++;               // counts 4,5,6 -> 3
        }
        check(count == 3);
    }
}

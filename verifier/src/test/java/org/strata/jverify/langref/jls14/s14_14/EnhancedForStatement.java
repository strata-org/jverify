package org.strata.jverify.langref.jls14.s14_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.2 -- The enhanced for statement: iterating over an array with for (T x : arr).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_14.EnhancedForStatement#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EnhancedForStatement {
    static void run() {
        int[] values = {2, 4, 6, 8};
        int sum = 0;
        for (int v : values) {
            sum += v;
        }
        check(sum == 20);
    }
}

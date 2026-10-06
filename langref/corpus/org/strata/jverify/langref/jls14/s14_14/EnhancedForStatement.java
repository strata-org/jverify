package org.strata.jverify.langref.jls14.s14_14;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.2 -- The enhanced for statement: iterating over an array with for (T x : arr).
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

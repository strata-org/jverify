package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.3 -- Array Access Expressions: indexing, read/write, computed indices, multi-dim access
class ArrayAccessExpressions_5 {
    static void run() {

        int[] a = new int[]{5, 6, 7, 8};

        // write access then read back
        a[2] = 99;

        // compound assignment through an array access
        a[0] += 10;
        check(a[0] == 15);
    }
}

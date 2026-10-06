package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.3 -- Array Access Expressions: indexing, read/write, computed indices, multi-dim access
class ArrayAccessExpressions_6 {
    static void run() {

        int[] a = new int[]{5, 6, 7, 8};

        // write access then read back
        a[2] = 99;

        // compound assignment through an array access
        a[0] += 10;

        // array access as an operand in arithmetic
        int sum = a[0] + a[1] + a[2] + a[3];
        check(sum == 15 + 6 + 99 + 8);
    }
}

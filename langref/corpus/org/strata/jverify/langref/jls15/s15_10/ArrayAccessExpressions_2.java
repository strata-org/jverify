package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.3 -- Array Access Expressions: indexing, read/write, computed indices, multi-dim access
class ArrayAccessExpressions_2 {
    static void run() {

        int[] a = new int[]{5, 6, 7, 8};
        check(a[3] == 8);
    }
}

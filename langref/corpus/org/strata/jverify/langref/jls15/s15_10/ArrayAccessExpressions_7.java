package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.3 -- Array Access Expressions: indexing, read/write, computed indices, multi-dim access
class ArrayAccessExpressions_7 {
    static void run() {

        // multidimensional array access
        int[][] m = new int[][]{{1, 2}, {3, 4}};
        check(m[0][0] == 1);
    }
}

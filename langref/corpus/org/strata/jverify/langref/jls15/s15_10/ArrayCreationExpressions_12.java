package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_12 {
    static void run() {

        // multidimensional with only the first dimension sized (ragged)
        int[][] r = new int[2][];
        r[0] = new int[]{1, 2};
        check(r[0][1] == 2);
    }
}

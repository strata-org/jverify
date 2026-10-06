package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_5 {
    static void run() {

        // multidimensional array with all dimensions sized
        int[][] m = new int[2][3];
        check(m.length == 2);
    }
}

package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_1 {
    static void run() {

        // new with a dimension expression; default-initialized to 0
        int[] a = new int[3];
        check(a.length == 3);
    }
}

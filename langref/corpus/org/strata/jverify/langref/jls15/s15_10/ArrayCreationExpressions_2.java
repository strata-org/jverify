package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_2 {
    static void run() {

        // new with a dimension expression; default-initialized to 0
        int[] a = new int[3];
        check(a[0] == 0 && a[1] == 0 && a[2] == 0);
    }
}

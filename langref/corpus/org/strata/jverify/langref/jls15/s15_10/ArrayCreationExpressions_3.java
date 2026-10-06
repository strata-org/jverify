package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_3 {
    static void run() {

        // new with array initializer
        int[] b = new int[]{10, 20, 30};
        check(b.length == 3);
    }
}

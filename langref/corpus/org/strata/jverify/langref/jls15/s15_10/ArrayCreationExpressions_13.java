package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_13 {
    static void run() {

        // dimension expression computed at runtime
        int n = 1 + 1;
        boolean[] flags = new boolean[n + 2];
        check(flags.length == 4);
    }
}

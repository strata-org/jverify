package org.strata.jverify.langref.jls15.s15_10;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
class ArrayCreationExpressions_16 {
    static void run() {

        // reference-type array, default-initialized to null
        String[] s = new String[2];
        check(s[0] == null);
    }
}

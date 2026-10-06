package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: unboxing a null reference at run time throws
// NullPointerException.
class CastExpression_UnboxNullNPE {
    static void run() {

        Integer boxed = null;
        boolean caught = false;
        try {
            int i = (int) boxed;
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}

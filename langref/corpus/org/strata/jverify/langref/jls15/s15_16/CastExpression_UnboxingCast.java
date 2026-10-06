package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a cast in unboxing context converts an Integer
// value to int.
class CastExpression_UnboxingCast {
    static void run() {

        Integer boxed = 7;
        int i = (int) boxed;
        check(i == 7);
    }
}

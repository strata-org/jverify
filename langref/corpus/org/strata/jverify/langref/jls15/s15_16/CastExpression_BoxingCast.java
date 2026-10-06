package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a cast in boxing context converts an int value
// to Integer (autoboxing).
class CastExpression_BoxingCast {
    static void run() {

        Integer i = (Integer) 5;
        check(i.intValue() == 5);
    }
}

package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a parenthesized type applied to a value,
// exercising primitive narrowing/widening casts, a reference (downcast) cast,
// and unboxing via cast.
class CastExpression_3 {
    static void run() {

        // Narrowing double -> int (truncation toward zero).
        int truncated = (int) 3.9;
        check(truncated == 3);
    }
}

class Base_CastExpression_3 {
    int v;
    Base_CastExpression_3(int v) { this.v = v; }
    int value() { return v; }
}

class Sub_CastExpression_3 extends Base_CastExpression_3 {
    Sub_CastExpression_3(int v) { super(v); }
}

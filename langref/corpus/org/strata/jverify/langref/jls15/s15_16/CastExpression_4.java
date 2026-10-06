package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a parenthesized type applied to a value,
// exercising primitive narrowing/widening casts, a reference (downcast) cast,
// and unboxing via cast.
class CastExpression_4 {
    static void run() {

        // Reference downcast: Object holding a Sub_CastExpression_4, cast back to Sub_CastExpression_4.
        Object o = new Sub_CastExpression_4(5);
        Sub_CastExpression_4 s = (Sub_CastExpression_4) o;
        check(s.value() == 5);
    }
}

class Base_CastExpression_4 {
    int v;
    Base_CastExpression_4(int v) { this.v = v; }
    int value() { return v; }
}

class Sub_CastExpression_4 extends Base_CastExpression_4 {
    Sub_CastExpression_4(int v) { super(v); }
}

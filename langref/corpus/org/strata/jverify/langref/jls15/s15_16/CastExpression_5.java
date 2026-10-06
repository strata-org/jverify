package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a parenthesized type applied to a value,
// exercising primitive narrowing/widening casts, a reference (downcast) cast,
// and unboxing via cast.
class CastExpression_5 {
    static void run() {

        // Reference downcast: Object holding a Sub_CastExpression_5, cast back to Sub_CastExpression_5.
        Object o = new Sub_CastExpression_5(5);
        Sub_CastExpression_5 s = (Sub_CastExpression_5) o;

        // Cast to a supertype (widening reference), then back.
        Base_CastExpression_5 base = (Base_CastExpression_5) s;
        check(base.value() == 5);
    }
}

class Base_CastExpression_5 {
    int v;
    Base_CastExpression_5(int v) { this.v = v; }
    int value() { return v; }
}

class Sub_CastExpression_5 extends Base_CastExpression_5 {
    Sub_CastExpression_5(int v) { super(v); }
}

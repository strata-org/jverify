package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a parenthesized type applied to a value,
// exercising primitive narrowing/widening casts, a reference (downcast) cast,
// and unboxing via cast.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_1#run")
class CastExpression_1 {
    static void run() {

        // Primitive widening cast (int -> long).
        long w = (long) 7;
        check(w == 7L);
    }
}

class Base_CastExpression_1 {
    int v;
    Base_CastExpression_1(int v) { this.v = v; }
    int value() { return v; }
}

class Sub_CastExpression_1 extends Base_CastExpression_1 {
    Sub_CastExpression_1(int v) { super(v); }
}

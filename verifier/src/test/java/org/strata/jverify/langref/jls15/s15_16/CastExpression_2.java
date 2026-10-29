package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a parenthesized type applied to a value,
// exercising primitive narrowing/widening casts, a reference (downcast) cast,
// and unboxing via cast.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastExpression_2 {
    static void run() {

        // Primitive narrowing cast (int -> byte), truncation per JLS 5.1.3.
        byte b = (byte) 300; // 300 = 0x12C -> low 8 bits 0x2C = 44
        check(b == 44);
    }
}

class Base_CastExpression_2 {
    int v;
    Base_CastExpression_2(int v) { this.v = v; }
    int value() { return v; }
}

class Sub_CastExpression_2 extends Base_CastExpression_2 {
    Sub_CastExpression_2(int v) { super(v); }
}

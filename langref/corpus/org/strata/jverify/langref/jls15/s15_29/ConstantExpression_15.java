package org.strata.jverify.langref.jls15.s15_29;

import static org.strata.jverify.JVerify.*;

// JLS 15.29 -- Constant Expressions: literals of primitive/String type, casts to
// primitive/String, the unary/binary/ternary operators applied to constant
// expressions, and references to final variables initialized with constant
// expressions. Constant expressions are evaluated at compile time; here we observe
// their runtime values and the contexts they enable (case labels, narrowing
// assignment conversion to byte/short/char).
class ConstantExpression_15 {

    // final fields initialized with constant expressions are themselves constants.
    static final int A = 10;
    static final int B = 32;
    static final String GREETING = "Hello, " + "World";

    static void run() {

        // Constant expression enabling narrowing assignment conversion (JLS 5.2):
        // an int constant in byte/short/char range may be assigned without a cast.
        byte bv = 100;          // 100 is a constant expression representable as byte
        check(bv == 100);
    }
}

package org.strata.jverify.langref.jls15.s15_29;

import static org.strata.jverify.JVerify.*;

// JLS 15.29 -- Constant Expressions: literals of primitive/String type, casts to
// primitive/String, the unary/binary/ternary operators applied to constant
// expressions, and references to final variables initialized with constant
// expressions. Constant expressions are evaluated at compile time; here we observe
// their runtime values and the contexts they enable (case labels, narrowing
// assignment conversion to byte/short/char).
class ConstantExpression_12 {

    // final fields initialized with constant expressions are themselves constants.
    static final int A = 10;
    static final int B = 32;
    static final String GREETING = "Hello, " + "World";

    static void run() {

        final String s = "a" + 1 + true;
        check(s.equals("a1true"));
    }
}

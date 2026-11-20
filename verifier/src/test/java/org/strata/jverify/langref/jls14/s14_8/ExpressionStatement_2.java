package org.strata.jverify.langref.jls14.s14_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.8 -- Expression Statements: only certain expressions may stand alone as
// statements (assignment, pre/post increment, pre/post decrement, method
// invocation, and class instance creation); each is evaluated for its effect.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_8.ExpressionStatement_2#run")
class ExpressionStatement_2 {
    static int side = 0;

    static int bump() {
        side = side + 1;     // method invocation expression statement (callee)
        return side;
    }

    static void run() {

        bump();              // method invocation expression statement
        bump();
        check(side == 2);
    }
}

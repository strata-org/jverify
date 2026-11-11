package org.strata.jverify.langref.jls14.s14_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.8 -- Expression Statements: only certain expressions may stand alone as
// statements (assignment, pre/post increment, pre/post decrement, method
// invocation, and class instance creation); each is evaluated for its effect.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_8.ExpressionStatement_3#run")
class ExpressionStatement_3 {
    static int side = 0;

    static class Counter {
        Counter() {
            side = side + 1;     // side effect of constructor invocation
        }
    }

    static void run() {

        new Counter();           // class instance creation expression statement
        new Counter();           // class instance creation expression statement
        check(side == 2);
    }
}

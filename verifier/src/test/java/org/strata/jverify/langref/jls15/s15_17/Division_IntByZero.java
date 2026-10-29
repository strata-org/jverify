package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division by zero throws
// ArithmeticException at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.Division_IntByZero#run")
class Division_IntByZero {
    static void run() {

        boolean caught = false;
        try {
            int r = 5 / 0;
        } catch (ArithmeticException e) {
            caught = true;
        }
        check(caught);
    }
}

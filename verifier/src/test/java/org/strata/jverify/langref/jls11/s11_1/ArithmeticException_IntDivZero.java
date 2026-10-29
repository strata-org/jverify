package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArithmeticException from integer division by zero.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.ArithmeticException_IntDivZero#run")
class ArithmeticException_IntDivZero {
    static void run() {
        int a = 5, b = 0;
        boolean caught = false;
        try {
            int c = a / b;
        } catch (ArithmeticException e) {
            caught = true;
        }
        check(caught);
    }
}

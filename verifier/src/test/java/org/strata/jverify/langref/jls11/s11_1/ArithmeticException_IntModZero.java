package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArithmeticException from integer remainder by zero.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.ArithmeticException_IntModZero#run")
class ArithmeticException_IntModZero {
    static void run() {
        int a = 1, b = 0;
        boolean caught = false;
        try {
            int c = a % b;
        } catch (ArithmeticException e) {
            caught = true;
        }
        check(caught);
    }
}

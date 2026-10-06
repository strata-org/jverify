package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArithmeticException from integer division by zero.
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

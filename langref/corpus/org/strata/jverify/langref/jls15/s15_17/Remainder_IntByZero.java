package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: integer remainder by zero throws
// ArithmeticException at run time.
class Remainder_IntByZero {
    static void run() {

        boolean caught = false;
        try {
            int r = 5 % 0;
        } catch (ArithmeticException e) {
            caught = true;
        }
        check(caught);
    }
}

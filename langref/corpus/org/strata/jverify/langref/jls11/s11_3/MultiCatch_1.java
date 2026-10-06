package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.20 -- multi-catch: a single catch clause can list several
// exception types separated by '|'.
class MultiCatch_1 {
    static void run() {
        boolean caught = false;
        try {
            throw new ArithmeticException("a");
        } catch (ArithmeticException | NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}

package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NumberFormatException from parseInt of a non-numeric string.
class NumberFormatException_1 {
    static void run() {
        boolean caught = false;
        try {
            Integer.parseInt("xyz");
        } catch (NumberFormatException e) {
            caught = true;
        }
        check(caught);
    }
}

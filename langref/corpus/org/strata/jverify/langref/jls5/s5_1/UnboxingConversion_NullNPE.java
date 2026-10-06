package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: unboxing a null wrapper reference throws NullPointerException.
class UnboxingConversion_NullNPE {
    static void run() {

        Integer x = null;
        boolean caught = false;
        try {
            int i = x;
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}

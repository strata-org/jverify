package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 (via JLS 5.1.3) -- Narrowing a NaN double to int yields 0.
class FloatingPointOperations_NarrowingNaN {
    static void run() {

        int r = (int) Double.NaN;
        check(r == 0);
    }
}

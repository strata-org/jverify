package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 (via JLS 5.1.3) -- Narrowing a positive double to int rounds toward zero (truncates).
class FloatingPointOperations_NarrowingTruncate {
    static void run() {

        int r = (int) 3.7;
        check(r == 3);
    }
}

package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 (via JLS 5.1.3) -- Narrowing +infinity double to int yields Integer.MAX_VALUE.
class FloatingPointOperations_NarrowingPosInf {
    static void run() {

        int r = (int) Double.POSITIVE_INFINITY;
        check(r == Integer.MAX_VALUE);
    }
}

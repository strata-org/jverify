package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.1 -- Integral Types and Values: byte, short, int, long, char ranges and values.
class IntegralTypesAndValues_8 {
    static void run() {

        int imin = -2147483648;
        check(imin == -2147483648);
    }
}

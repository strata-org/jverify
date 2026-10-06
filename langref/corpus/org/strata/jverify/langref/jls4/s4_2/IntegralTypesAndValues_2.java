package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.1 -- Integral Types and Values: byte, short, int, long, char ranges and values.
class IntegralTypesAndValues_2 {
    static void run() {

        short s = 32767;
        check(s == 32767);
    }
}

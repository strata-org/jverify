package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.1 -- Integral Types and Values: byte, short, int, long, char ranges and values.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegralTypesAndValues_9#run")
class IntegralTypesAndValues_9 {
    static void run() {

        long lmin = -9223372036854775808L;
        check(lmin == -9223372036854775808L);
    }
}

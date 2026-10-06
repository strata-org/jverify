package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: long overflow wraps modulo 2^64,
// so Long.MAX_VALUE + 1 == Long.MIN_VALUE.
class IntegerOperations_LongOverflow {
    static void run() {

        long v = Long.MAX_VALUE + 1L;
        check(v == Long.MIN_VALUE);
    }
}

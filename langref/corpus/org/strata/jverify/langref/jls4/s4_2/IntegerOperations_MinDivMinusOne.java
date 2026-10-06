package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: Integer.MIN_VALUE / -1 overflows silently to
// Integer.MIN_VALUE; no exception is thrown.
class IntegerOperations_MinDivMinusOne {
    static void run() {

        int r = Integer.MIN_VALUE / -1;
        check(r == Integer.MIN_VALUE);
    }
}

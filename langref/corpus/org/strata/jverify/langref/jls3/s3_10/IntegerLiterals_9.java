package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: Long.MIN_VALUE expressed as -9223372036854775808L.
class IntegerLiterals_9 {
    static void run() {

        long minL = Long.MIN_VALUE;
        check(minL == -9223372036854775808L);
    }
}

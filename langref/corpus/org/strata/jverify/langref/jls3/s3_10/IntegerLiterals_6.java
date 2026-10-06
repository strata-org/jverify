package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: decimal, hex, octal, binary, underscores, long suffix
class IntegerLiterals_6 {
    static void run() {

        long big = 9_000_000_000L;
        check(big == 9000000000L);
    }
}

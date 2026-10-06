package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: decimal, hex, octal, binary, underscores, long suffix
class IntegerLiterals_5 {
    static void run() {

        int under = 1_000_000;
        check(under == 1000000);
    }
}

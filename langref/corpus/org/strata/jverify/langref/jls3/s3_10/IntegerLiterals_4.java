package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: decimal, hex, octal, binary, underscores, long suffix
class IntegerLiterals_4 {
    static void run() {

        int bin = 0b101010;
        check(bin == 42);
    }
}

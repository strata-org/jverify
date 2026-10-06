package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: decimal, hex, octal, binary, underscores, long suffix
class IntegerLiterals_7 {
    static void run() {

        int maxInt = 2147483647;
        check(maxInt == 0x7fffffff);
    }
}

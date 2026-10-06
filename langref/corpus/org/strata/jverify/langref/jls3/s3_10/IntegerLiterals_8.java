package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: Integer.MIN_VALUE expressed as -2147483648.
class IntegerLiterals_8 {
    static void run() {

        int min = Integer.MIN_VALUE;
        check(min == -2147483648);
    }
}

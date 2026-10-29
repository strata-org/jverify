package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: Integer.MIN_VALUE expressed as -2147483648.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.IntegerLiterals_8#run")
class IntegerLiterals_8 {
    static void run() {

        int min = Integer.MIN_VALUE;
        check(min == -2147483648);
    }
}

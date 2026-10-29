package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: 64-bit all-ones hex literal equals -1L.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.IntegerLiterals_10#run")
class IntegerLiterals_10 {
    static void run() {

        long ffff = 0xFFFF_FFFF_FFFF_FFFFL;
        check(ffff == -1L);
    }
}

package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: underscores permitted between digits of a binary literal.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.IntegerLiterals_13#run")
class IntegerLiterals_13 {
    static void run() {

        int v = 0b1010_1010;
        check(v == 170);
    }
}

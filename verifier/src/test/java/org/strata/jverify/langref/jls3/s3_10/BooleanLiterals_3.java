package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.3 -- Boolean Literals: true and false
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.BooleanLiterals_3#run")
class BooleanLiterals_3 {
    static void run() {

        boolean t = true;
        boolean f = false;
        check(t != f);
    }
}

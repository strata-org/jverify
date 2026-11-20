package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: the value of an assignment expression is the value assigned.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_44#run")
class Operators_44 {
    static void run() {

        int a;
        check((a = 5) == 5);
    }
}

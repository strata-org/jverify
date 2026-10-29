package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.5 -- The boolean Type and boolean Values: true/false, logical and conditional operators.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.BooleanType_13#run")
class BooleanType_13 {
    static void run() {

        boolean t = true;
        boolean t2 = true;
        // equality
        check((t == t2));
    }
}

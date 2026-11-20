package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.2 -- Boolean Equality Operators == and !=: logical equality on boolean operands
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.BooleanEqualityOperators_3#run")
class BooleanEqualityOperators_3 {
    static void run() {

        boolean t = true;
        boolean f = false;
        check(t != f);
    }
}

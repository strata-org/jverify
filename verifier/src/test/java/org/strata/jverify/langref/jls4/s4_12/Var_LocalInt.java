package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12 -- Local Variable Declarations: 'var' infers the local's static type
// from the initializer (JLS 14.4 / 14.4.1).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.Var_LocalInt#run")
class Var_LocalInt {
    static void run() {

        var x = 3;
        check(x == 3);
    }
}

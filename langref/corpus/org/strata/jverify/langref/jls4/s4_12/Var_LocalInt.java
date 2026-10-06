package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12 -- Local Variable Declarations: 'var' infers the local's static type
// from the initializer (JLS 14.4 / 14.4.1).
class Var_LocalInt {
    static void run() {

        var x = 3;
        check(x == 3);
    }
}

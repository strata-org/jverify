package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12 -- Local Variable Declarations: 'var' infers a parameterized reference
// type from the initializer (JLS 14.4 / 14.4.1).
class Var_LocalArrayList {
    static void run() {

        var l = new java.util.ArrayList<Integer>();
        l.add(1);
        check(l.get(0) == 1);
    }
}

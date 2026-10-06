package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.2 -- Boolean Equality Operators == and !=: logical equality on boolean operands
class BooleanEqualityOperators_2 {
    static void run() {

        boolean f = false;
        check(f == f);
    }
}

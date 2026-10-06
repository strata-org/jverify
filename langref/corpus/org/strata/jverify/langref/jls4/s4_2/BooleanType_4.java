package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.5 -- The boolean Type and boolean Values: true/false, logical and conditional operators.
class BooleanType_4 {
    static void run() {

        boolean t = true;
        boolean f = false;
        check((t & f) == false);
    }
}

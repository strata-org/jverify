package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.5 -- The boolean Type and boolean Values: true/false, logical and conditional operators.
class BooleanType_6 {
    static void run() {

        boolean f = false;
        check((f | f) == false);
    }
}

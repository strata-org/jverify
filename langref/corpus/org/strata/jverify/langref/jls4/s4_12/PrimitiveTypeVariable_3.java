package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.1 -- Variables of Primitive Type: a primitive-typed variable always
// holds a value of that exact primitive type.
class PrimitiveTypeVariable_3 {
    static void run() {

        double d = 3.5;
        check(d == 3.5);
    }
}

package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.1 -- Variables of Primitive Type: a primitive-typed variable always
// holds a value of that exact primitive type.
class PrimitiveTypeVariable_5 {
    static void run() {

        char c = 'Z';
        check(c == 'Z');
    }
}

package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.1 -- Variables of Primitive Type: a primitive-typed variable always
// holds a value of that exact primitive type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.PrimitiveTypeVariable_1#run")
class PrimitiveTypeVariable_1 {
    static void run() {

        int i = 42;
        check(i == 42);
    }
}

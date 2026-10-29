package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.1 -- Subtyping among Primitive Types: the direct supertype chain
// double >1 float >1 long >1 int >1 {short, char}, short >1 byte.
// Exercised via widening assignments that require no explicit cast.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.PrimitiveSubtyping_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class PrimitiveSubtyping_2 {
    static void run() {

        byte b = 7;
        short s = b;        // byte <: short
        int i = s;
        check(i == 7);
    }
}

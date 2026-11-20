package org.strata.jverify.langref.jls12.s12_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.7 / 15.9 -- `new String(...)` always creates a fresh instance,
// distinct in identity from the interned string literal, though it compares
// equal by value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_7.StringInterning_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ERROR})
class StringInterning_3 {
    static void run() {
        String a = new String("x");
        String b = "x";
        check(a != b && a.equals(b));
    }
}

package org.strata.jverify.langref.jls12.s12_7;

import static org.strata.jverify.JVerify.*;

// JLS 12.7 / 15.9 -- `new String(...)` always creates a fresh instance,
// distinct in identity from the interned string literal, though it compares
// equal by value.
class StringInterning_3 {
    static void run() {
        String a = new String("x");
        String b = "x";
        check(a != b && a.equals(b));
    }
}

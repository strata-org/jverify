package org.strata.jverify.langref.jls12.s12_7;

import static org.strata.jverify.JVerify.*;

// JLS 12.7 -- `String.intern()` returns a canonical representation of the
// string that is `==` to any literal with the same character sequence.
class StringInterning_4 {
    static void run() {
        String a = new String("y").intern();
        String b = "y";
        check(a == b);
    }
}

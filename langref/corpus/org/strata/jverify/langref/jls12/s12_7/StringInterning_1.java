package org.strata.jverify.langref.jls12.s12_7;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 / 12.7 -- String literals refer to the same instance of `String`
// via interning: two occurrences of the same literal are the same object.
class StringInterning_1 {
    static void run() {
        String a = "abc";
        String b = "abc";
        check(a == b);
    }
}

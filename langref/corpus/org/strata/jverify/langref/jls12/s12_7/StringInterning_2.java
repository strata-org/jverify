package org.strata.jverify.langref.jls12.s12_7;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 / 3.10.5 -- Compile-time constant expressions of type String are
// computed at compile time; the concatenation "ab" + "cd" is a constant
// expression, produces the interned "abcd", and is `==` to the literal
// "abcd".
class StringInterning_2 {
    static void run() {
        String a = "ab" + "cd";
        String b = "abcd";
        check(a == b);
    }
}

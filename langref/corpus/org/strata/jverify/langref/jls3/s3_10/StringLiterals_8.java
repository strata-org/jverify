package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: Unicode escape inside a string literal is processed
// before lexical analysis; the escape resolves to U+00E9 (LATIN SMALL LETTER E WITH ACUTE).
class StringLiterals_8 {
    static void run() {

        String s = "caf\u00E9";
        check(s.length() == 4);
    }
}

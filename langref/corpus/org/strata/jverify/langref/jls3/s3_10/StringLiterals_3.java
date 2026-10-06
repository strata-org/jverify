package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: double-quoted strings, escapes, concatenation, char access
class StringLiterals_3 {
    static void run() {

        String escaped = "a\tb\"c";
        check(escaped.charAt(1) == '\t');
    }
}

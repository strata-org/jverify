package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: double-quoted strings, escapes, concatenation, char access
class StringLiterals_1 {
    static void run() {

        String s = "hello";
        check(s.length() == 5);
    }
}

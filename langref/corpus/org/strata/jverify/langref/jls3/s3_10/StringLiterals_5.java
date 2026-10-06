package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: double-quoted strings, escapes, concatenation, char access
class StringLiterals_5 {
    static void run() {

        String concat = "foo" + "bar";
        check(concat.equals("foobar"));
    }
}

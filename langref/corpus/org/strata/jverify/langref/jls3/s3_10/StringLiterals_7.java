package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: identical string literals refer to the same interned String instance.
class StringLiterals_7 {
    static void run() {

        String a = "abc";
        String b = "abc";
        check(a == b);
    }
}

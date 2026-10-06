package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.1 -- Integer Literals: lowercase 'l' long suffix.
class IntegerLiterals_11 {
    static void run() {

        long v = 42l;
        check(v == 42L);
    }
}

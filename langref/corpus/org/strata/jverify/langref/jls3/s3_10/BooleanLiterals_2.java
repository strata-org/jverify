package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.3 -- Boolean Literals: true and false
class BooleanLiterals_2 {
    static void run() {

        boolean f = false;
        check(!f);
    }
}

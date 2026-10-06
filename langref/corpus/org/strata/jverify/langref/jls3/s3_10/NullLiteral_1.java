package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.8 -- The Null Literal: null assignable to reference types, equality, assignment
class NullLiteral_1 {
    static void run() {

        String s = null;
        check(s == null);
    }
}

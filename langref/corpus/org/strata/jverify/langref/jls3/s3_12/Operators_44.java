package org.strata.jverify.langref.jls3.s3_12;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: the value of an assignment expression is the value assigned.
class Operators_44 {
    static void run() {

        int a;
        check((a = 5) == 5);
    }
}

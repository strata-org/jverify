package org.strata.jverify.langref.jls3.s3_12;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: the '->' lambda arrow token.
class Operators_43 {
    static void run() {

        java.util.function.IntUnaryOperator f = x -> x + 1;
        check(f.applyAsInt(3) == 4);
    }
}

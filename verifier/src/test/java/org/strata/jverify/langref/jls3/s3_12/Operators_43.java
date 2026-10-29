package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: the '->' lambda arrow token.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_43#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Operators_43 {
    static void run() {

        java.util.function.IntUnaryOperator f = x -> x + 1;
        check(f.applyAsInt(3) == 4);
    }
}

package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_30#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Operators_30 {
    static void run() {

        check((-1 >>> 28) == 0xF);
    }
}

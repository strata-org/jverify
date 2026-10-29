package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_37#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Operators_37 {
    static void run() {

        // compound assignment operators
        int e = 8;
        e += 2;
        e -= 3;
        e *= 4;
        e /= 7;
        e %= 3;
        e <<= 5;
        check(e == 32);
    }
}

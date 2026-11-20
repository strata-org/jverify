package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: narrowing conversion (double -> int) of
// positive infinity saturates to Integer.MAX_VALUE per JLS 5.1.3.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_PosInfToInt#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastExpression_PosInfToInt {
    static void run() {

        int r = (int) Double.POSITIVE_INFINITY;
        check(r == Integer.MAX_VALUE);
    }
}

package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators: positive and negative zero
// compare equal under ==.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.NumericalEquality_PosNegZero#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericalEquality_PosNegZero {
    static void run() {

        check(0.0 == -0.0);
    }
}

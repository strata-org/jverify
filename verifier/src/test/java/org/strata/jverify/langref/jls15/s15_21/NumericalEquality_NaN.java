package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators: NaN is never equal to any value,
// including itself, per IEEE 754.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.NumericalEquality_NaN#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericalEquality_NaN {
    static void run() {

        check(Double.NaN != Double.NaN);
    }
}

package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators: autoboxing Integer values in the
// cached range [-128, 127] returns the same reference, so == holds.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.ReferenceEquality_IntegerCache127#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ReferenceEquality_IntegerCache127 {
    static void run() {

        Integer a = 127;
        Integer b = 127;
        check(a == b);
    }
}

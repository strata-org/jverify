package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators: Integer values just outside the
// cached range (>= 128) yield distinct references, so == is false.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.ReferenceEquality_IntegerCache128#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ReferenceEquality_IntegerCache128 {
    static void run() {

        Integer a = 128;
        Integer b = 128;
        check(a != b);
    }
}

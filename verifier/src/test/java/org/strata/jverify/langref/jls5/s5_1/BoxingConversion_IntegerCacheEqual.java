package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: boxed int values in the range -128..127 must be
// cached (Integer.valueOf), so autoboxed Integer 127 objects are identity-equal.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.BoxingConversion_IntegerCacheEqual#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class BoxingConversion_IntegerCacheEqual {
    static void run() {

        Integer a = 127;
        Integer b = 127;
        check(a == b);
    }
}

package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.1 -- upper-bounded wildcard formal parameter type: a method taking
// List<? extends Number> can consume any List whose element type is Number or
// a subtype (Integer, Double, ...).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.WildcardParamExtends_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class WildcardParamExtends_1 {
    static int sum(java.util.List<? extends Number> l) {
        int s = 0;
        for (Number n : l) {
            s += n.intValue();
        }
        return s;
    }

    static void run() {
        check(sum(java.util.List.of(1, 2, 3)) == 6);
    }
}

package org.strata.jverify.langref.jls14.s14_30;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.1 -- Kinds of Patterns: a type pattern declares a local variable of a single type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_30.TypePattern#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TypePattern {
    static void run() {
        Object o = Integer.valueOf(7);
        int result = 0;
        // Type pattern `Integer i` binds o to a fresh local variable i of type Integer.
        if (o instanceof Integer i) {
            result = i.intValue();
        }
        check(result == 7);
    }
}

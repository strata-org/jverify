package org.strata.jverify.langref.jls14.s14_30;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.2 -- Pattern Matching: a value matches a type pattern iff it is non-null and an instance of the type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_30.PatternMatching_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class PatternMatching_3 {
    static void run() {

        Object nullValue = null;
        // null never matches a type pattern (JLS 14.30.2).
        boolean c = nullValue instanceof String;
        check(!c);
    }
}

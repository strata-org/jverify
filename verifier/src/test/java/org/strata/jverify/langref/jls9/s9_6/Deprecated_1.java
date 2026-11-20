package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.6 -- @Deprecated: applied to a class, it is visible at runtime via
// isAnnotationPresent (java.lang.Deprecated has RUNTIME retention).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.Deprecated_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Deprecated_1 {
    @Deprecated
    static class C {
    }

    static void run() {
        check(C.class.isAnnotationPresent(Deprecated.class));
    }
}

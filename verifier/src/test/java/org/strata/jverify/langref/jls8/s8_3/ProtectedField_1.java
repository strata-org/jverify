package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1 -- protected field access: a protected instance field is inherited
// by subclasses and readable via subclass instances.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.ProtectedField_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class ProtectedField_1 {
    static class B {
        protected int v = 5;
    }

    static class S extends B {
    }

    static void run() {
        check(new S().v == 5);
    }
}

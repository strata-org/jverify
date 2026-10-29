package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1 -- private field access: a private static field is accessible from
// within the same class.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.PrivateField_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class PrivateField_1 {
    private static int secret = 42;

    static void run() {
        check(secret == 42);
    }
}

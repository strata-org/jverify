package org.strata.jverify.langref.jls6.s6_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.6.1 -- Determining Accessibility: a private member is accessible only
// within the body of the top-level class that encloses the declaration of the
// member. Here `secret` is accessed from within its own class.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_6.AccessPrivate_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class AccessPrivate_1 {
    private static int secret = 42;

    static void run() {
        check(secret == 42);
    }
}

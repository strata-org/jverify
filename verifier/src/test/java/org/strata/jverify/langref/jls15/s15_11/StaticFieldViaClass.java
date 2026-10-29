package org.strata.jverify.langref.jls15.s15_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.11 -- Field Access: static field access via the class name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_11.StaticFieldViaClass#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StaticFieldViaClass {
    static class C {
        static int SHARED = 42;
    }

    static void run() {

        check(C.SHARED == 42);
    }
}

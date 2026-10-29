package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.1 -- a user-defined subclass of RuntimeException is unchecked
// and can be thrown/caught without a throws declaration.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.CustomUnchecked_ThrowCatch#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CustomUnchecked_ThrowCatch {
    static class MyEx extends RuntimeException {
        MyEx(String m) { super(m); }
    }

    static void run() {
        String msg = null;
        try {
            throw new MyEx("custom");
        } catch (MyEx e) {
            msg = e.getMessage();
        }
        check(msg != null && msg.equals("custom"));
    }
}

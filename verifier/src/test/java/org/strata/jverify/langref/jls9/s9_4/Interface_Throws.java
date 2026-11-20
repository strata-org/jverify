package org.strata.jverify.langref.jls9.s9_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.4 -- an interface abstract method may declare a checked-exception
// throws clause; an implementing method may keep or narrow it.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_4.Interface_Throws#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Interface_Throws {
    interface I {
        int m() throws Exception;
    }

    static class C implements I {
        public int m() throws Exception {
            return 5;
        }
    }

    static void run() {
        boolean ok = false;
        try {
            ok = new C().m() == 5;
        } catch (Exception e) {
        }
        check(ok);
    }
}

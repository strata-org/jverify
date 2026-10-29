package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.6 -- a method that can throw a checked exception must declare it
// in a throws clause; callers must catch or re-declare.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.Throws_CheckedException#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Throws_CheckedException {
    static class MyChecked extends Exception {
        MyChecked(String m) { super(m); }
    }

    static int m() throws MyChecked {
        throw new MyChecked("x");
    }

    static void run() {
        boolean caught = false;
        try {
            m();
        } catch (MyChecked e) {
            caught = true;
        }
        check(caught);
    }
}

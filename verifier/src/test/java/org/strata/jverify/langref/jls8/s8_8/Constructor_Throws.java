package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.5 -- a constructor may declare checked exceptions in a throws
// clause; callers of `new` must handle them.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.Constructor_Throws#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Constructor_Throws {
    static class MyChecked extends Exception {
        MyChecked(String m) { super(m); }
    }

    static class Blob {
        Blob() throws MyChecked {
            throw new MyChecked("ctor");
        }
    }

    static void run() {
        boolean caught = false;
        try {
            Blob b = new Blob();
        } catch (MyChecked e) {
            caught = true;
        }
        check(caught);
    }
}

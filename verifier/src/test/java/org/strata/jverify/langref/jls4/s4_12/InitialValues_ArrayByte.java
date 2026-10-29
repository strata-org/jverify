package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: byte[] components default to 0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_ArrayByte#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class InitialValues_ArrayByte {
    static void run() {

        byte[] a = new byte[2];
        check(a[0] == 0 && a[1] == 0);
    }
}

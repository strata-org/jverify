package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: short[] components default to 0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_ArrayShort#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class InitialValues_ArrayShort {
    static void run() {

        short[] a = new short[2];
        check(a[0] == 0 && a[1] == 0);
    }
}

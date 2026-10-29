package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: float[] components default to +0.0f.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_ArrayFloat#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InitialValues_ArrayFloat {
    static void run() {

        float[] a = new float[2];
        check(a[0] == 0.0f && a[1] == 0.0f);
    }
}

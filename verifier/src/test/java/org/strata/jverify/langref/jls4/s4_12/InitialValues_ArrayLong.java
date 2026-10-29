package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: long[] components default to 0L.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_ArrayLong#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class InitialValues_ArrayLong {
    static void run() {

        long[] a = new long[2];
        check(a[0] == 0L && a[1] == 0L);
    }
}

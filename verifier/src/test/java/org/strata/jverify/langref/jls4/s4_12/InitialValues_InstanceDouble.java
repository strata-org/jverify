package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance double field defaults to +0.0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_InstanceDouble#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InitialValues_InstanceDouble {
    double v;

    static void run() {

        InitialValues_InstanceDouble o = new InitialValues_InstanceDouble();
        check(o.v == 0.0);
    }
}

package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance long field defaults to 0L.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_InstanceLong#run")
class InitialValues_InstanceLong {
    long v;

    static void run() {

        InitialValues_InstanceLong o = new InitialValues_InstanceLong();
        check(o.v == 0L);
    }
}

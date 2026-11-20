package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance short field defaults to 0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_InstanceShort#run")
class InitialValues_InstanceShort {
    short v;

    static void run() {

        InitialValues_InstanceShort o = new InitialValues_InstanceShort();
        check(o.v == 0);
    }
}

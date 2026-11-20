package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance byte field defaults to 0.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_InstanceByte#run")
class InitialValues_InstanceByte {
    byte v;

    static void run() {

        InitialValues_InstanceByte o = new InitialValues_InstanceByte();
        check(o.v == 0);
    }
}

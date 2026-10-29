package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance char field
// defaults to the null character (U+0000).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.InitialValues_InstanceChar#run")
class InitialValues_InstanceChar {
    char v;

    static void run() {

        InitialValues_InstanceChar o = new InitialValues_InstanceChar();
        check(o.v == 0);
    }
}

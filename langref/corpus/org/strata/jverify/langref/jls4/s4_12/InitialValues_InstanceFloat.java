package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance float field defaults to +0.0f.
class InitialValues_InstanceFloat {
    float v;

    static void run() {

        InitialValues_InstanceFloat o = new InitialValues_InstanceFloat();
        check(o.v == 0.0f);
    }
}

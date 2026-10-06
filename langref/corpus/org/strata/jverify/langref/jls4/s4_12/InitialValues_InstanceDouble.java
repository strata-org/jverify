package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance double field defaults to +0.0.
class InitialValues_InstanceDouble {
    double v;

    static void run() {

        InitialValues_InstanceDouble o = new InitialValues_InstanceDouble();
        check(o.v == 0.0);
    }
}

package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized static float field defaults to +0.0f.
class InitialValues_StaticFloat {
    static float f;

    static void run() {

        check(f == 0.0f);
    }
}

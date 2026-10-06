package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: float[] components default to +0.0f.
class InitialValues_ArrayFloat {
    static void run() {

        float[] a = new float[2];
        check(a[0] == 0.0f && a[1] == 0.0f);
    }
}

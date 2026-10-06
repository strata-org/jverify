package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: double[] components default to +0.0.
class InitialValues_ArrayDouble {
    static void run() {

        double[] a = new double[2];
        check(a[0] == 0.0 && a[1] == 0.0);
    }
}

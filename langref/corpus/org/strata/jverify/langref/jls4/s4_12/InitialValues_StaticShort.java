package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized static short field defaults to 0.
class InitialValues_StaticShort {
    static short s;

    static void run() {

        check(s == 0);
    }
}

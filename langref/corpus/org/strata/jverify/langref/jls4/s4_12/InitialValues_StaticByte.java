package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized static byte field defaults to 0.
class InitialValues_StaticByte {
    static byte b;

    static void run() {

        check(b == 0);
    }
}

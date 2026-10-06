package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: an uninitialized instance char field
// defaults to the null character (U+0000).
class InitialValues_InstanceChar {
    char v;

    static void run() {

        InitialValues_InstanceChar o = new InitialValues_InstanceChar();
        check(o.v == 0);
    }
}

package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: charAt returns the char code unit at the given index.
class TheClassString_CharAt {
    static void run() {

        check("abc".charAt(1) == 'b');
    }
}

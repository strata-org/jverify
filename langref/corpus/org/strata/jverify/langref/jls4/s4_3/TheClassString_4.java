package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: string literals, interning, and the + concatenation operator.
class TheClassString_4 {
    static void run() {

        // String concatenation produces a new String equal to the concatenation.
        String c = "ab" + "cd";
        check(c.equals("abcd"));
    }
}

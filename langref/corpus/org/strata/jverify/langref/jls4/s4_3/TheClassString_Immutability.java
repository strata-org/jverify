package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: String objects are immutable; concatenation
// produces a new String and does not mutate the original.
class TheClassString_Immutability {
    static void run() {

        String a = "ab";
        String b = a + "cd";
        check(a.equals("ab") && b.equals("abcd"));
    }
}

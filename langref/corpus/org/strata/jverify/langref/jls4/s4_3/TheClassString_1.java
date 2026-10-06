package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: string literals, interning, and the + concatenation operator.
class TheClassString_1 {
    static void run() {

        // String objects represent sequences of Unicode code points; literals are interned.
        String a = "hello";
        String b = "hel" + "lo";   // compile-time constant, same interned instance
        check(a == b);
    }
}

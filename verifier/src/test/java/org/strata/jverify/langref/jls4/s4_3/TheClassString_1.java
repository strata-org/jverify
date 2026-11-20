package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: string literals, interning, and the + concatenation operator.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ERROR})
class TheClassString_1 {
    static void run() {

        // String objects represent sequences of Unicode code points; literals are interned.
        String a = "hello";
        String b = "hel" + "lo";   // compile-time constant, same interned instance
        check(a == b);
    }
}

package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: string literals, interning, and the + concatenation operator.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TheClassString_2 {
    static void run() {

        // String objects represent sequences of Unicode code points; literals are interned.
        String a = "hello";
        String b = "hel" + "lo";
        check(a.equals(b));
    }
}

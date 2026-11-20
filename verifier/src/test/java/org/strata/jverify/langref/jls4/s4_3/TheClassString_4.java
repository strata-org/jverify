package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: string literals, interning, and the + concatenation operator.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TheClassString_4 {
    static void run() {

        // String concatenation produces a new String equal to the concatenation.
        String c = "ab" + "cd";
        check(c.equals("abcd"));
    }
}

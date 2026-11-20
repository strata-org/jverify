package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: String objects are immutable; concatenation
// produces a new String and does not mutate the original.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_Immutability#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TheClassString_Immutability {
    static void run() {

        String a = "ab";
        String b = a + "cd";
        check(a.equals("ab") && b.equals("abcd"));
    }
}

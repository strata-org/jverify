package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: String.intern() returns the canonical String
// from the string pool, which is identity-equal to the literal.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_InternExplicit#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TheClassString_InternExplicit {
    static void run() {

        String a = new String("y").intern();
        String b = "y";
        check(a == b);
    }
}

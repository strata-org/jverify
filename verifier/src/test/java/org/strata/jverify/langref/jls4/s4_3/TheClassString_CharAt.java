package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: charAt returns the char code unit at the given index.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_CharAt#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TheClassString_CharAt {
    static void run() {

        check("abc".charAt(1) == 'b');
    }
}

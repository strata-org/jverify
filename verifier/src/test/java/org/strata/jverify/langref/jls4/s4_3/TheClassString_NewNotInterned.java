package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.3 -- The Class String: 'new String(literal)' creates a fresh String instance
// that is not identity-equal to the interned literal, but is content-equal.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.TheClassString_NewNotInterned#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ERROR})
class TheClassString_NewNotInterned {
    static void run() {

        String a = new String("hi");
        String b = "hi";
        check(a != b && a.equals(b));
    }
}

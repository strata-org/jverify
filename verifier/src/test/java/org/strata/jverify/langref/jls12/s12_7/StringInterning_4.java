package org.strata.jverify.langref.jls12.s12_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.7 -- `String.intern()` returns a canonical representation of the
// string that is `==` to any literal with the same character sequence.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_7.StringInterning_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringInterning_4 {
    static void run() {
        String a = new String("y").intern();
        String b = "y";
        check(a == b);
    }
}

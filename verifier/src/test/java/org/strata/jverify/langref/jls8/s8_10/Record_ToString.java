package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.3 -- a record provides an automatic toString() of the form
// TypeName[c1=v1, c2=v2, ...] built from the component values.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_ToString#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Record_ToString {
    record P(int x, int y) {
    }

    static void run() {
        P a = new P(1, 2);
        check(a.toString().equals("P[x=1, y=2]"));
    }
}

package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.3 -- a record automatically provides an equals(Object) that returns
// true when all components compare equal.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_Equality#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Record_Equality {
    record P(int x, int y) {
    }

    static void run() {
        P a = new P(1, 2);
        P b = new P(1, 2);
        check(a.equals(b));
    }
}

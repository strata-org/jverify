package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.3 -- a record provides an automatic hashCode() derived from its
// components; equal records must have equal hash codes.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_HashCode#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Record_HashCode {
    record P(int x) {
    }

    static void run() {
        P a = new P(5);
        P b = new P(5);
        check(a.hashCode() == b.hashCode());
    }
}

package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.2 -- record body may declare static members, including static
// factory methods that construct record instances.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_StaticFactory#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Record_StaticFactory {
    record P(int x) {
        public static P zero() {
            return new P(0);
        }
    }

    static void run() {
        check(P.zero().x() == 0);
    }
}

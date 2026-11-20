package org.strata.jverify.langref.jls8.s8_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.4.2 -- a compact canonical constructor can validate arguments and
// throw before the implicit field assignments, aborting construction.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_10.Record_CompactValidationThrows#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class Record_CompactValidationThrows {
    record Pos(int v) {
        public Pos {
            if (v < 0) {
                throw new IllegalArgumentException();
            }
        }
    }

    static void run() {
        boolean caught = false;
        try {
            new Pos(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        check(caught);
    }
}

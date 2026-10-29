package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: try / catch / finally / throw.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_9#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Keywords_9 {
    static void run() {

        boolean caught = false;
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            caught = true;
        } finally {
        }
        check(caught);
    }
}

package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the 'throws' clause on a method declaration.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_14#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Keywords_14 {
    static void run() {

        check(m() == 1);
    }

    static int m() throws RuntimeException {
        return 1;
    }
}

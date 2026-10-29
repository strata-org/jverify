package org.strata.jverify.langref.jls15.s15_27;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.2 -- Lambda Body: an unchecked exception thrown from within a
// lambda propagates out of the functional interface method invocation.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_27.LambdaBody_Throws#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LambdaBody_Throws {
    static void run() {

        Runnable r = () -> { throw new RuntimeException("x"); };
        boolean caught = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}

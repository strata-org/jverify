package org.strata.jverify.langref.jls15.s15_27;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.2 -- Lambda Body: a void-returning lambda body can capture an
// effectively final local of array type and mutate its contents.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_27.LambdaBody_Runnable#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LambdaBody_Runnable {
    static void run() {

        int[] sink = new int[1];
        Runnable r = () -> sink[0] = 7;
        r.run();
        check(sink[0] == 7);
    }
}

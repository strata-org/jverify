package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12 -- Method Invocation: a varargs method invoked with several
// arguments receives an array of that length.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.VarargsInvocation_Three#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class VarargsInvocation_Three {
    static int count(int... xs) { return xs.length; }

    static void run() {

        check(count(1, 2, 3) == 3);
    }
}

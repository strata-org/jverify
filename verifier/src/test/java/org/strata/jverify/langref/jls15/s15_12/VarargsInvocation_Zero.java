package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12 -- Method Invocation: a varargs method invoked with no arguments
// receives a zero-length array.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.VarargsInvocation_Zero#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class VarargsInvocation_Zero {
    static int count(int... xs) { return xs.length; }

    static void run() {

        check(count() == 0);
    }
}

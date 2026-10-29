package org.strata.jverify.langref.jls9.s9_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.8 -- a functional interface may be the target type of a method
// reference; the referenced method supplies the implementation.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_8.FunctionalInterface_MethodRef#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FunctionalInterface_MethodRef {
    interface I {
        int apply(int x);
    }

    static int neg(int x) {
        return -x;
    }

    static void run() {
        I i = FunctionalInterface_MethodRef::neg;
        check(i.apply(5) == -5);
    }
}

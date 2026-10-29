package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.9 -- @FunctionalInterface: marks an interface as having exactly one abstract
// method; here it is implemented by a lambda expression and invoked.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.FunctionalInterfaceAnnotation#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FunctionalInterfaceAnnotation {
    @FunctionalInterface
    interface IntOp {
        int apply(int a, int b);
    }

    static void run() {
        IntOp add = (a, b) -> a + b;
        check(add.apply(2, 3) == 5);
    }
}

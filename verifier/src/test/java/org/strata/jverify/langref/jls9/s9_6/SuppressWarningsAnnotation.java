package org.strata.jverify.langref.jls9.s9_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.5 -- @SuppressWarnings: instructs the compiler to suppress named warnings
// (here "unchecked") for the annotated method; program behavior is unchanged.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_6.SuppressWarningsAnnotation#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SuppressWarningsAnnotation {
    @SuppressWarnings("unchecked")
    static int compute() {
        java.util.List raw = new java.util.ArrayList();
        raw.add(Integer.valueOf(42));
        Object o = raw.get(0);
        return (Integer) o;
    }

    static void run() {
        check(compute() == 42);
    }
}

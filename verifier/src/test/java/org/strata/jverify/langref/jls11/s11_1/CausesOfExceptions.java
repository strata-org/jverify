package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- The Causes of Exceptions: exceptions arise from a throw
// statement, an abnormal execution (e.g. division by zero), or an
// asynchronous internal error; here we exercise throw and abnormal execution.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.CausesOfExceptions#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CausesOfExceptions {
    static void run() {
        int causes = 0;

        // Cause 1: an explicit throw statement.
        try {
            throw new RuntimeException("explicit throw");
        } catch (RuntimeException e) {
            causes += 1;
        }

        // Cause 2: abnormal completion of an expression evaluation,
        // here an integer division by zero raising ArithmeticException.
        int zero = 0;
        try {
            int bad = 10 / zero;
            causes += bad; // unreachable
        } catch (ArithmeticException e) {
            causes += 2;
        }

        check(causes == 3);
    }
}

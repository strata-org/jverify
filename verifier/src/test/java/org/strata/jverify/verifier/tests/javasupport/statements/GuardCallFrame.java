package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Regression test: a method CALL in a while guard must keep its heap
 * effect visible to modifies/frame inference. BodyTranslator's side tables
 * (calleeKeys and friends) are identity-keyed, so any pass that rebuilds a
 * guard's call node detaches them — a detached node once made
 * {@code drain}'s write to {@code c.count} invisible, letting
 * {@code caller} prove the stale {@code c.count == 5}.
 */
@JVerifyTest(exitCode = 4, errorCount = 1, methodsVerified = 3, methodsInvalid = 1)
class GuardCallFrame {
    static class Counter {
        int count;
    }

    static int drain(Counter c) {
        if (c.count > 0) {
            c.count = c.count - 1;
        }
        return c.count;
    }

    static void caller() {
        Counter c = new Counter();
        c.count = 5;
        while (drain(c) > 0) {
        }
        check(c.count == 5);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}

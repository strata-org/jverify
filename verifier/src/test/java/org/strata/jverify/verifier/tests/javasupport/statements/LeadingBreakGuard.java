package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A user-written leading {@code if (c) break;} at the top of a
 * {@code while (true)} / {@code for (;;)} body is the loop's own pre-test
 * (the old pre-tested-while recovery made it the loop condition): the
 * {@code invariant(...)} right after it is the loop's contract and must be
 * promoted, not refused as a misplaced contract call.
 */
@JVerifyTest(exitCode = 0, methodsVerified = 3)
class LeadingBreakGuard {

    static int whileTrue(int n) {
        precondition(0 <= n && n <= 1000);
        int i = 0;
        while (true) {
            if (i >= n) {
                break;
            }
            invariant(0 <= i && i <= n);
            i = i + 1;
        }
        check(0 <= i && i <= n);
        return i;
    }

    static int bareFor(int n) {
        precondition(0 <= n && n <= 1000);
        int i = 0;
        for (;;) {
            if (i >= n) {
                break;
            }
            invariant(0 <= i && i <= n);
            i = i + 1;
        }
        check(0 <= i && i <= n);
        return i;
    }
}

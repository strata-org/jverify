package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.invariant;

/**
 * Pins `continue <label>` binding to the labeled OUTER loop (follow-up to
 * D566037730, which was the same wrong-target bug class for break): were the
 * continue bound to the inner loop, the inner step would run and j == 0 would
 * be violated; bound correctly, the inner loop never completes, so `after`
 * is never incremented.
 *
 * <p>Kept separate from VerifyBreakContinue and expected to fail: the
 * unconditional labeled continue to the outer loop aborts without a verdict
 * (exit 2).
 */
@JVerifyTest(expectedToFail = true, blockedBy = Blocker.FRONTEND_OTHER, methodsVerified = 2)
class LabeledContinue {
    static void labeledContinueOuter() {
        int after = 0;
        outer:
        for (int i = 0; i < 3; i = i + 1) {
            invariant(i >= 0 && i <= 3);
            invariant(after == 0);
            for (int j = 0; j < 2; j = j + 1) {
                invariant(j == 0);
                continue outer;
            }
            after = after + 1;
        }
        check(after == 0);
    }
}

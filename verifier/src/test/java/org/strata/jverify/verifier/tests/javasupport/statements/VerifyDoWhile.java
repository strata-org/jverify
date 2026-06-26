package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConstantValue"})
@JVerifyTest(exitCode = 4, methodsVerified = 8, methodsInvalid = 3, errorCount = 3)
class VerifyDoWhile {
    /**
     * do-while with break exiting the loop early.
     */
    static void doWhileWithBreak() {
        int x = 0;
        do {
            invariant(x >= 0 && x <= 3);
            if (x == 3) {
                break;
            }
            x = x + 1;
        } while (x < 10);
        check(x == 3);
    }

    /**
     * do-while with continue re-evaluating the condition.
     * When continue is hit, execution should skip the rest of the body
     * but still check the while-condition.
     */
    static void doWhileWithContinue() {
        int x = 0;
        int sum = 0;
        do {
            invariant(x >= 0 && x <= 5);
            invariant(x <= 2 ? sum == x : sum == 2);
            if (x >= 2) {
                x = x + 1;
                continue;
            }
            sum = sum + 1;
            x = x + 1;
        } while (x < 5);
        check(sum == 2);
    }

    /**
     * Numeric loop variable bounded by the invariant.
     * Exercises the sentinel-weakening case (Bug 2) from the original review in #418.
     */
    static void doWhileNumericInvariant() {
        int x = 0;
        do {
            invariant(0 <= x && x < 100);
            x = x + 1;
        } while (x < 5);
        check(x >= 5);
    }

    /**
     * Known-limitation test documenting the completeness gap of the
     * {@code while(true) + exit(!cond)} do-while desugaring.
     *
     * <p>Because the modeled loop guard is the constant {@code true}, the verifier never
     * learns {@code !cond} at the normal condition-exit, so a postcondition that depends
     * on the exit condition cannot be proved. Here {@code check(x == 3)} fails even though
     * the identical head-tested {@code while} (see {@link #whileKeepsExitCondition()}) proves it. 
     * This is not rescuable with a stronger invariant.
     *
     * <p>The clean fix is a body-tested loop encoding at the Laurel level, tracked in
     * strata-org/Strata#1350. Until then this case is expected to be rejected, and this
     * test pins that behavior so the limitation is visible and the fix is detectable.
     */
    static void doWhileForgetsExitCondition() {
        int x = 0;
        do {
            invariant(0 <= x && x <= 3);
            x = x + 1;
        } while (x < 3);
        check(x == 3);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    /**
     * Head-tested {@code while} counterpart of {@link #doWhileForgetsExitCondition()}, with
     * the identical body, invariant, and postcondition. Here {@code check(x == 3)} IS proved:
     * the modeled guard is the real condition {@code x < 3}, so at loop exit the verifier has
     * {@code !(x < 3)} (i.e. {@code x >= 3}) which, combined with the invariant {@code x <= 3},
     * gives {@code x == 3}.
     *
     * <p>This is the behavior the do-while desugaring loses by modeling the guard as constant
     * {@code true}, and is the gap that strata-org/Strata#1350 would close.
     */
    static void whileKeepsExitCondition() {
        int x = 0;
        while (x < 3) {
            invariant(0 <= x && x <= 3);
            x = x + 1;
        }
        check(x == 3);
    }

    /**
     * Nested do-while loops, each exited via an unlabeled {@code break}.
     * Exercises two stacked do-while label frames: the inner {@code break} must
     * resolve to the inner loop's break label and the outer {@code break} to the
     * outer loop's, leaving the loop variables at their break-time values.
     * The postconditions are derived from the break guards (not the loop
     * conditions), so they are unaffected by the exit-condition gap.
     */
    static void nestedDoWhileBreak() {
        int i = 0;
        do {
            invariant(0 <= i && i <= 3);
            int j = 0;
            do {
                invariant(0 <= j && j <= 2);
                if (j == 2) {
                    break;
                }
                j = j + 1;
            } while (j < 100);
            check(j == 2);
            if (i == 3) {
                break;
            }
            i = i + 1;
        } while (i < 100);
        check(i == 3);
    }

    /**
     * Labeled do-while with {@code break} to the outer label from inside a nested loop.
     * Exercises the {@code javaLabel = pendingLabel} capture in the do-while desugaring:
     * the outer loop's label frame must carry {@code "outer"} so that {@code break outer}
     * resolves to the outer break label rather than the inner one. The break fires on a
     * known state ({@code i == 2}), so the post-loop assertion is provable.
     */
    static void labeledDoWhileBreakToOuter() {
        int i = 0;
        outer:
        do {
            invariant(0 <= i && i <= 2);
            int j = 0;
            do {
                invariant(0 <= j && j <= 5);
                if (i == 2) {
                    break outer;
                }
                j = j + 1;
            } while (j < 3);
            i = i + 1;
        } while (i < 100);
        check(i == 2);
    }

    /**
     * Labeled do-while with {@code continue} to the outer label from inside a nested loop.
     * {@code continue outer} must resolve to the outer loop's continue label (re-evaluating
     * the outer condition rather than the inner one), relying on the {@code javaLabel}
     * capture.
     */
    static void labeledDoWhileContinueToOuter() {
        int i = 0;
        outer:
        do {
            invariant(0 <= i && i <= 3);
            i = i + 1;
            int j = 0;
            do {
                invariant(0 <= j && j <= 3);
                if (j == 0) {
                    continue outer;
                }
                j = j + 1;
            } while (j < 3);
        } while (i < 3);
    }


    /**
     * Negative regression test: the invariant holds on entry but is not preserved by the loop body.
     * Once x reaches 5 the body increments it to 6 while the loop continues (6 < 10), violating x <= 5.
     * This MUST be rejected by the verifier.
     */
    static void whileBadInvariant() {
        var x = 0;
        while (x < 10) {
            invariant(x >= 0 && x <= 5);
//                    ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            x = x + 1;
        }
    }

    /**
     * Negative regression test for unsoundness case (Bug 3) from the original review in #418:
     * The invariant is FALSE on the first iteration (x == -1 violates x >= 0).
     * This MUST be rejected by the verifier.
     */
    static void doWhileBadInitialInvariant() {
        int x = -1;
        do {
            invariant(x >= 0);
//                    ^^^^^^ Error: assertion does not hold
            x = x + 1;
        } while (x < 5);
    }
}

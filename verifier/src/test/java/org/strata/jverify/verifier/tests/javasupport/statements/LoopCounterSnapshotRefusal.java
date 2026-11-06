package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A snapshot is refused -- fail closed, as a misplaced contract call -- when a
 * header statement writes a variable the snapshot names, on either side of the
 * copy:
 *
 * <ul>
 * <li>a store embedded in the loop condition writes the copied variable BEFORE
 * the copy: the invariant is checked at the loop head, before the condition
 * runs, so there {@code i0 = i} would bind the counter's pre-increment value
 * and the substituted invariant would say something else than the copy
 * computes;</li>
 * <li>a jump guard between the copy and the markers writes the snapshot
 * itself AFTER the copy: {@code t} no longer holds what the recorded
 * definition says, so substituting it away would turn the false
 * {@code invariant(t == i)} into the trivially true {@code i == i}.</li>
 * </ul>
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 1, methodsSkipped = 2, errorCount = 0)
class LoopCounterSnapshotRefusal {

    static int conditionWritesTheCounter(int n) {
//             ^ error: call to contract method must come before the rest of the body
        precondition(0 <= n && n <= 1000);
        int i = 0;
        int steps = 0;
        while (i++ < n) {
            final int i0 = i;
            invariant(forall((int j) -> implies(0 <= j && j < i0, j <= n)));
            steps = steps + 1;
        }
        return steps;
    }

    static int guardWritesTheSnapshot(int n) {
//             ^ error: call to contract method must come before the rest of the body
        precondition(0 <= n && n <= 100);
        int i = 0;
        int t = 0;
        while (true) {
            t = i;
            if (t++ < -1) {
                break;
            }
            invariant(0 <= i && i <= n);
            invariant(t == i);  // false in Java: t == i + 1 here
            if (i >= n) {
                break;
            }
            i = i + 1;
        }
        return i;
    }
}

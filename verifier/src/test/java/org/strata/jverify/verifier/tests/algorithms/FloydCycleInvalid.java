package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR}, exitCode = 4)
class FloydCycleInvalid {
    @Pure
    @Erased
    static boolean closed(int[] next) {
        reads(next);
        return forall((int i) -> implies(0 <= i && i < next.length, 0 <= next[i] && next[i] < next.length));
    }

    @Pure
    @Erased
    static int iterate(int[] next, int x, @Unbounded int k) {
        reads(next);
        decreases(k);
        if (k <= 0) {
            return x;
        }
        return iterate(next, next[x], k - 1);
    }

    static int cycleLength(int[] next, int x0) {
        precondition(closed(next));
        precondition(next.length <= 100000);
        precondition(0 <= x0 && x0 < next.length);
        postcondition((int lambda) -> 1 <= lambda && lambda <= next.length);
        postcondition((int lambda) -> exists((int mu) -> 0 <= mu && mu <= next.length
                && iterate(next, x0, mu + lambda) == iterate(next, x0, mu)));

        int tortoise = next[x0];
        int hare = next[next[x0]];
        @Unbounded int steps = 1;
        while (tortoise != hare) {
            invariant(1 <= steps && steps <= next.length);
            invariant(tortoise == iterate(next, x0, steps));
            invariant(hare == iterate(next, x0, 2 * steps));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            decreases(next.length - steps);

            tortoise = next[tortoise];
            hare = next[hare];
            steps = steps + 1;
        }

        int lambda = 1;
        int probe = next[tortoise];
        while (probe != tortoise) {
            invariant(1 <= lambda && lambda <= next.length);
            invariant(probe == iterate(next, tortoise, lambda));
            decreases(next.length - lambda);

            probe = next[probe];
            lambda = lambda + 1;
        }
        return lambda;
    }
}

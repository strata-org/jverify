package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nat;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_DECREASES}, exitCode = 4)
class FibonacciIterativeInvalid {
    @Transparent
    @Erased
    static @Unbounded @Nat int spec(@Unbounded @Nat int n) {
        decreases(n);
        if (n < 2) {
            return n;
        }
        return spec(n - 1) + spec(n - 2);
    }

    static @Nat int fib(@Nat int n) {
        postcondition((int result) -> result == spec(n));
        precondition(spec(n) <= Integer.MAX_VALUE);

        if (n == 0) {
            return 0;
        }

        int previousResult = 0;
        int result = 1;
        int i = 1;
        while (i < n) {
            invariant(result == spec(i));
            invariant(previousResult == spec(i - 1));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            invariant(1 <= i && i <= n);

            i = i + 1;
            specIsIncreasing(i, n);
            int addition = result + previousResult;
            result = addition;
            previousResult = result;
        }
        return result;
    }

    @Erased
    static void specIsIncreasing(@Unbounded @Nat int i, @Unbounded @Nat int j) {
        precondition(i <= j);
        postcondition(spec(i) <= spec(j));

        @Unbounded @Nat int x = i;
        while (x < j) {
            invariant(x <= j);
            invariant(spec(i) <= spec(x));
            x = x + 1;
        }
    }
}

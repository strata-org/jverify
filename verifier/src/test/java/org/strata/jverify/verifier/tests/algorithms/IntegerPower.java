package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nat;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_DECREASES}, exitCode = 0)
class IntegerPower {
    @Pure
    @Erased
    static @Unbounded @Nat int powSpec(@Unbounded @Nat int base, @Unbounded @Nat int exp) {
        decreases(exp);
        if (exp == 0) {
            return 1;
        }
        return base * powSpec(base, exp - 1);
    }

    static int power(@Nat int base, @Nat int exp) {
        precondition(0 <= base && base <= 3);
        precondition(0 <= exp && exp <= 19);
        precondition(powSpec(base, exp) <= Integer.MAX_VALUE);
        postcondition((int res) -> res == powSpec(base, exp));

        int acc = 1;
        int i = 0;
        while (i < exp) {
            invariant(0 <= i && i <= exp);
            invariant(acc == powSpec(base, i));

            acc = acc * base;
            i = i + 1;
        }
        return acc;
    }
}

package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class LongPostconditions {
    static long twice(long x) {
        precondition(-1000L <= x && x <= 1000L);
        postcondition((long r) -> r == 2 * x);
        return 2 * x;
    }

    static long clamp(long x, long lo, long hi) {
        precondition(lo <= hi);
        postcondition((long r) -> lo <= r && r <= hi);
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }

    static void universalQuantifier() {
        check(forall((long n) -> implies(n > 0L, n >= 1L)));
    }

    // The bound variable ranges over long, beyond the int range
    static void existentialQuantifier() {
        check(exists((long n) -> n > 4294967296L));
    }

    static void boundedQuantifier() {
        check(forall((long n) -> -9223372036854775808L <= n && n <= 9223372036854775807L));
    }
}

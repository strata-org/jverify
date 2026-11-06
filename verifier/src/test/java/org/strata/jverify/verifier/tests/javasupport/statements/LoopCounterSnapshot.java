package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Quantifying over the loop prefix with the counter snapshot idiom. A lambda
 * in a contract clause cannot capture the loop counter (javac requires
 * captured locals to be effectively final), so the counter is copied into a
 * final local before the invariants; the copy is a ghost definition the
 * extraction substitutes away ({@code i0 -> i}), so the invariant is checked
 * with the counter's value at the loop head, exactly as if the copy ran
 * first on every iteration.
 */
@JVerifyTest(exitCode = 0)
class LoopCounterSnapshot {

    /** A per-character parser's invariant: every element before {@code i} is lower-case. */
    static boolean allLower(byte[] s) {
        postcondition((boolean r) -> implies(r,
                forall((int j) -> implies(0 <= j && j < s.length, s[j] >= 97 && s[j] <= 122))));
        int i = 0;
        while (i < s.length) {
            final int i0 = i;
            invariant(0 <= i && i <= s.length);
            invariant(forall((int j) -> implies(0 <= j && j < i0, s[j] >= 97 && s[j] <= 122)));
            if (s[i] < 97 || s[i] > 122) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    /** The snapshot still precedes the markers when a short-circuit {@code &&} nests them. */
    static int boundedCount(int n, int m) {
        precondition(0 <= n && n <= 1000);
        precondition(0 <= m && m <= 1000);
        int i = 0;
        while (i < n && i < m) {
            final int i0 = i;
            invariant(0 <= i && i <= n && i <= m);
            invariant(forall((int j) -> implies(0 <= j && j < i0, j < n)));
            i = i + 1;
        }
        return i;
    }

    /** Chained snapshots: a copy of a copy resolves to the original counter. */
    static int chained(int n) {
        precondition(0 <= n && n <= 1000);
        int i = 0;
        while (i < n) {
            final int a = i;
            final int b = a;
            invariant(0 <= b && b <= n);
            invariant(forall((int j) -> implies(0 <= j && j < b, j < n)));
            i = i + 1;
        }
        return i;
    }
}

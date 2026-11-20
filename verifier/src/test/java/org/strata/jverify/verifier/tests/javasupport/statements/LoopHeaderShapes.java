package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Loops whose header compiles to more than a single test, with the loop
 * emitted as one labelled block that keeps the header inside it: the
 * {@code invariant(...)} markers follow the header statements (implicit
 * throw guards, chained short-circuit tests, condition-embedded stores) and
 * must still be found, and a local first stored in the header must stay
 * visible after the loop and to the invariants. As everywhere, an invariant
 * must bound every variable the loop mutates (the loop head havocs them, and
 * int obligations need both bounds back).
 */
@JVerifyTest(exitCode = 0, methodsVerified = 10)
class LoopHeaderShapes {

    /** A division in the condition: its implicit divide-by-zero guard precedes the markers. */
    static int divideCondition(int d) {
        precondition(d > 0);
        int x = 100;
        while (x / d > 0) {
            invariant(0 <= x && x <= 100);
            x = x - 1;
        }
        check(x >= 0);
        return x;
    }

    /** A short-circuit {@code &&}: the second test's arm nests the markers. */
    static int andCondition(int n, int m) {
        precondition(n >= 0);
        precondition(m >= 0);
        int i = 0;
        while (i < n && i < m) {
            invariant(0 <= i && i <= n && i <= m);
            i = i + 1;
        }
        check(i >= 0);
        return i;
    }

    /** A short-circuit {@code ||}: the body, markers included, is duplicated into each test's arm. */
    static int orCondition(int n, int m) {
        precondition(n >= 0);
        precondition(m >= 0);
        int i = 0;
        while (i < n || i < m) {
            invariant(0 <= i && (i <= n || i <= m));
            i = i + 1;
        }
        check(i >= 0);
        return i;
    }

    /** A compound {@code for}: several header stores around the short-circuit tests. */
    static int compoundFor(int n, int m) {
        precondition(n >= 0);
        precondition(m >= 0);
        int s = 0;
        for (int i = 0, j = 0; i < n && j < m; i = i + 1, j = j + 1) {
            invariant(0 <= i && i == j && s == i && i <= n && j <= m);
            s = s + 1;
        }
        check(s >= 0);
        return s;
    }

    /** The single-test baseline must keep working unchanged. */
    static int plainWhile(int n) {
        precondition(n >= 0);
        int i = 0;
        while (i < n) {
            invariant(0 <= i && i <= n);
            i = i + 1;
        }
        check(i >= 0);
        return i;
    }

    static int step(int i) {
        precondition(i > 0);
        postcondition((int r) -> r == i - 1);
        return i - 1;
    }

    /** A local first stored in the loop condition is still visible after the loop. */
    static int readAll(int seed) {
        precondition(0 < seed && seed <= 100);
        int x;
        while ((x = step(seed)) != 0) {
            invariant(0 < seed && seed <= 100);
            seed = x;
        }
        return x;
    }

    /** An invariant may mention the header-stored local (resolved in the enclosing scope). */
    static int readAllWithInvariantOnHeaderLocal(int seed) {
        precondition(0 < seed && seed <= 100);
        int x;
        while ((x = step(seed)) != 0) {
            invariant(0 < seed && seed <= 100);
            invariant(x > 0 || x <= 0);
            seed = x;
        }
        return x;
    }

    /** A loop body ending in an if/else whose arms end in assignments of different types. */
    static int meet(int len) {
        precondition(len >= 0);
        boolean go = true;
        int fast = 0;
        int steps = 0;
        while (go) {
            invariant(0 <= fast && fast <= len && steps == fast);
            if (fast >= len) {
                go = false;
            } else {
                fast = fast + 1;
                steps = steps + 1;
            }
        }
        return steps;
    }
}

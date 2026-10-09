package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;
import org.strata.jverify.Unbounded;

/**
 * Mirrors Strata's T23b_IncrDecrField.lean test cases.
 * Tests increment/decrement on object fields.
 */
@JVerifyTest(methodsVerified = 13, errorCount = 0)
class IncrementDecrementField {
    static class Counter {
        int n;
    }

    // A method whose ONLY heap effect is `c.n++` (no preceding
    // assignment) must still get a synthesized modifies clause: the
    // heap-write fallback counts a field increment as a heap write
    // just as it counts an assignment. Without that clause the
    // method fails with "modifies clause could not be proved" at
    // (1:1-1:1).
    static class UnboundedCounter {
        @Unbounded int n;
    }
    static void bumpOnlyPostIncr(UnboundedCounter c) {
        c.n++;
    }

    static void bumpOnlyPreDecr(UnboundedCounter c) {
        --c.n;
    }

    static void postIncrFieldStatement() {
        Counter c = new Counter();
        c.n = 10;
        c.n++;
        check(c.n == 11);
    }

    static void preIncrFieldStatement() {
        Counter c = new Counter();
        c.n = 10;
        ++c.n;
        check(c.n == 11);
    }

    static void postDecrFieldStatement() {
        Counter c = new Counter();
        c.n = 10;
        c.n--;
        check(c.n == 9);
    }

    static void preDecrFieldStatement() {
        Counter c = new Counter();
        c.n = 10;
        --c.n;
        check(c.n == 9);
    }

    static void mixedFieldIncrDecrStatements() {
        Counter c = new Counter();
        c.n = 0;
        c.n++;
        c.n++;
        ++c.n;
        c.n--;
        check(c.n == 2);
    }

    static void postIncrFieldInExpression() {
        Counter c = new Counter();
        c.n = 5;
        int y = c.n++;
        check(c.n == 6);
        check(y == 5);
    }

    static void preIncrFieldInExpression() {
        Counter c = new Counter();
        c.n = 5;
        int y = ++c.n;
        check(c.n == 6);
        check(y == 6);
    }

    static void postDecrFieldInExpression() {
        Counter c = new Counter();
        c.n = 5;
        int y = c.n--;
        check(c.n == 4);
        check(y == 5);
    }
}

package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Mirrors Strata's T23b_IncrDecrField.lean test cases.
 * Tests increment/decrement on object fields.
 * Skipped: JVerify does not yet support instance field access.
 */
@JVerifyTest(skip = "JVerify: field increment requires class/instance support", exitCode = 0)
class IncrementDecrementField {
    static class Counter {
        int n;
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

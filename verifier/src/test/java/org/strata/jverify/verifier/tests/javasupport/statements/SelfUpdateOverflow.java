package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The overflow of a self-update ({@code x++}, {@code x += c}) is caught by
 * the range constraint at the assignment.
 * This pins that the constraint fires for every target kind — int local,
 * instance field, long local and array element (whose update goes through
 * the checked helper as well, so the operation's overflow obligation is
 * reported next to the constraint: only an int local has iinc) — and that a
 * bound on the old value discharges each.
 */
@JVerifyTest(exitCode = 4, errorCount = 11, methodsVerified = 6, methodsInvalid = 4)
class SelfUpdateOverflow {
    static class Counter {
        int n;
    }

    static int localIncrementUnbounded(int x) {
//  ^ Error: postcondition could not be proved
        x++;
//      ^^^^ Error: assertion could not be proved
        return x;
//      ^^^^^^^^^ Error: assertion could not be proved
    }

    static int localIncrementBounded(int x) {
        precondition(x < 1000);
        x++;
        return x;
    }

    static void fieldIncrementUnbounded(Counter c) {
        modifies(c);
        c.n++;
//      ^^^^^^ Error: assertion could not be proved
//      ^^^^^^ Error: no int overflow on '+' could not be proved
    }

    static void fieldIncrementBounded(Counter c) {
        precondition(c.n < 1000);
        modifies(c);
        c.n++;
    }

    static long longIncrementUnbounded(long x) {
//  ^ Error: postcondition does not hold
        x++;
//      ^^^^ Error: no long overflow on '+' does not hold
//      ^^^^ Error: assertion does not hold
        return x;
//      ^^^^^^^^^ Error: assertion does not hold
    }

    static long longIncrementBounded(long x) {
        precondition(x < 1000L);
        x++;
        return x;
    }

    static void arrayElementIncrementUnbounded(int[] a, int i) {
        precondition(0 <= i && i < a.length);
        modifies(a);
        a[i]++;
//      ^^^^^^^ Error: no int overflow on '+' could not be proved
//      ^^^^^^^ Error: int[] element range could not be proved
    }

    static void arrayElementIncrementBounded(int[] a, int i) {
        precondition(0 <= i && i < a.length && a[i] < 1000);
        modifies(a);
        a[i]++;
    }
}

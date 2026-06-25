package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Nat;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Exercises the {@code @Nat} and {@code @Unbounded} type-use annotations on
 * static method parameters, return types, and local variables (issue #402).
 *
 * <p>{@code @Nat} maps the integral type to a natural-number subset type
 * (e.g. {@code int} -> {@code nat31}), so a value known to be negative violates
 * the constraint. {@code @Unbounded} maps to Laurel's unbounded integer, so the
 * usual fixed-width overflow check is dropped.
 *
 * <p>Strata currently reports these subset-constraint violations as a generic
 * "assertion does not hold"; the more specific "subset constraints of ..."
 * wording is a separate Strata-side improvement (triage priority 12).
 */
@JVerifyTest(exitCode = 4, methodsVerified = 4, errorCount = 4)
class NatAndUnbounded {
    // A @Nat parameter becomes a precondition the body may rely on.
    static void natParam(@Nat int n) {
        check(n >= 0);
    }

    // A non-negative initializer satisfies the @Nat constraint.
    static void natLocalGood() {
        @Nat int x = 5;
        check(x >= 0);
    }

    // A negative initializer violates the @Nat constraint.
    static void natLocalBad() {
        @Nat int x = -1;
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // The @Nat constraint also applies on reassignment.
    static void natReassignBad(@Nat int n) {
        @Nat int x = n;
        x = -5;
//      ^^^^^^ Error: assertion does not hold
    }

    // @Nat works for the other integral widths too.
    static void natShortBad() {
        @Nat short s = -3;
//      ^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void natLongBad() {
        @Nat long l = -3;
//      ^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // @Unbounded drops the fixed-width overflow check, so this addition of two
    // arbitrary (possibly INT_MAX) values verifies.
    static @Unbounded int unboundedAdd(@Unbounded int a, @Unbounded int b) {
        return a + b;
    }
}

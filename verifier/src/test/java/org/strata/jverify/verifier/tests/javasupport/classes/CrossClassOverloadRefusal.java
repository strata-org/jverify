package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness case for an overload set spanning an inheritance chain, from a case
 * raised in review and reproduced here.
 *
 * <p>{@code BaseX.val(B)} and {@code SubX.val(A)} are OVERLOADS, not overrides.
 * Overload disambiguation names each by its declaring-class closure: {@code B}'s
 * declarer {@code BaseX} sees only {@code val(B)} (one signature) so it keeps the
 * bare name {@code val}; {@code SubX}'s closure sees both signatures so
 * {@code val(A)} becomes {@code val$A}. A call is emitted with the SAME rule
 * applied to the method javac bound, so {@code s.val(b)} -- javac binds
 * {@code BaseX.val(B)} ({@code B} more specific than {@code A}) -- emits the bare
 * {@code val} and Strata resolves it to {@code BaseX.val(B)}, which returns 0.
 *
 * <p>So {@code check(s.val(b) == 42)} is now correctly disproved: the earlier
 * risk was Strata re-resolving {@code val} by name to {@code SubX.val(A)}'s
 * {@code r == 42} and passing silently; the distinct emitted names remove that.
 *
 * <p>Verified 7 = both {@code val} bodies + one implicit constructor per class
 * (five); each verifies its own contract. Only {@code root} fails.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsVerified = 7, errorCount = 1)
public class CrossClassOverloadRefusal {

    static class A {}

    static class B extends A {}

    static class BaseX {
        @Transparent @Unbounded int val(B b) { return 0; }
    }

    static final class SubX extends BaseX {
        @Unbounded int val(A a) { postcondition((int r) -> r == 42); return 42; }
    }

    static void root(SubX s, B b) {
        check(s.val(b) == 42);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}

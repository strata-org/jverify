package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for an overload set spanning an inheritance chain, from a
 * case raised in review and reproduced here.
 *
 * <p>{@code BaseX.val(B)} and {@code SubX.val(A)} are OVERLOADS, not overrides,
 * and neither existing gate stops the call. {@code refuseIfOverloaded} counts
 * same-name methods in the immediate declaring class: 1 in each, refuses
 * neither. {@code refuseIfPolymorphicDispatch} correctly accepts -- receiver
 * static type {@code SubX} is {@code final}, so no override can exist.
 *
 * <p>The emitted {@code s#val(b)} drops the declaring class and signature javac
 * chose. javac binds {@code BaseX.val(B)} ({@code B} more specific than
 * {@code A}): returns 0, no contract. Strata re-resolves {@code #val} by name to
 * the most-specific DECLARER {@code SubX}, binding {@code SubX.val(A)}'s
 * {@code r == 42}; {@code b : B} is a subtype of that overload's {@code A}, so
 * the argument check passes silently. Measured before this fix: 0 errors, 0
 * skipped.
 *
 * <p>{@code refuseIfOverloadedAcrossAncestry} counts distinct erased signatures
 * over the receiver's supertype closure -- two here -- and refuses, degrading to
 * a skip. Right only while Strata resolves by name; by-signature resolution
 * would pick {@code BaseX.val(B)} as javac does and verify soundly.
 *
 * <p>The assertion is {@code == 42}, the value only the WRONG overload justifies:
 * remove the refusal and it passes, so a return to the false green shows up as a
 * missing diagnostic here.
 *
 * <p>Verified 7 = both {@code val} bodies + one implicit constructor per class
 * (five); they still verify their own contracts. Skipped 1 = {@code root}, the
 * call site.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 7, methodsSkipped = 1, errorCount = 0)
public class CrossClassOverloadRefusal {

    static class A {}

    static class B extends A {}

    static class BaseX {
        @Pure @Unbounded int val(B b) { return 0; }
    }

    static final class SubX extends BaseX {
        @Pure @Unbounded int val(A a) { postcondition((int r) -> r == 42); return 42; }
    }

    static void root(SubX s, B b) {
//              ^ error: overloaded method 'val' (2 signatures visible on 'org.strata.jverify.verifier.tests.javasupport.classes.CrossClassOverloadRefusal.SubX' and its supertypes); overloading is not supported
        // javac binds BaseX.val(B) -> 0; Strata's by-name re-resolution would
        // bind SubX.val(A)'s `r == 42` and verify this clean.
        check(s.val(b) == 42);
    }
}

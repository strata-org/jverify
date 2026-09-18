package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for the monomorphism gate on instance calls.
 *
 * <p>Laurel resolves {@code obj#method} against the receiver's DECLARED type with
 * no dynamic dispatch, so emitting {@code b.compute()} would route to
 * {@code BaseP}'s contract ({@code r >= 0}) even though {@code b} holds a
 * {@code SubP} whose override returns {@code -1}. JVerify enforces no
 * behavioural subtyping, so {@code SubP.compute} verifies vacuously against its
 * empty contract while {@code root} would falsely verify against the wrong
 * contract.
 *
 * <p>The fix refuses any call not provably monomorphic (callee/class final or
 * callee private). This one is none of those, so {@code root} is refused — a
 * graceful skip with a diagnostic (the enclosing method is static). Both
 * {@code compute} bodies and the three implicit constructors still verify; only
 * the unsound call site is dropped.
 *
 * <p>Re-enable verification of this call when runtime dispatch lands
 * (Strata#1174).
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 5, methodsSkipped = 1, errorCount = 0)
class PolyDispatchSoundness {
    static class BaseP {
        @Pure int compute() { postcondition((int r) -> r >= 0); return 0; }
    }
    static class SubP extends BaseP {
        @Pure @Override int compute() { return -1; }
    }
    static void root() {
//              ^ error: polymorphic dispatch through interface/superclass is not yet supported
        BaseP b = new SubP();
        check(b.compute() >= 0);
    }
}

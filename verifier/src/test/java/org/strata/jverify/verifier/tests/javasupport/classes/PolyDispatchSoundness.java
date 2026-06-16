package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for the static {@code Class_method} mangling encoding.
 *
 * <p>{@code Class_method} mangling resolves an instance call against the
 * receiver's STATIC type, so emitting {@code b.compute()} below would route to
 * {@code BaseP}'s contract ({@code r >= 0}) even though {@code b} holds a
 * {@code SubP} whose override returns {@code -1}. JVerify enforces no
 * behavioural subtyping, so {@code SubP.compute} verifies vacuously against its
 * own (empty) contract while {@code root} would falsely verify the
 * {@code check} against the wrong contract.
 *
 * <p>The fix refuses any call that is not provably monomorphic (callee/class
 * final or callee private). The call here is none of those, so {@code root} is
 * refused — translated to a graceful skip with a diagnostic (the enclosing
 * method is static) — rather than verified clean. Both {@code compute} bodies
 * and the three implicit constructors still verify on their own; only the
 * unsound call site is dropped.
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

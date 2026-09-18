package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness pin for the call side of the opted-out-overload relaxation
 * ({@link VerifyFalseOverload}):
 * both overloads mangle to the SAME Laurel name, so a call to the opted-out one
 * must NOT be emitted — it would bind to the emitted sibling's procedure (wrong
 * contract, wrong arity). The symbol-keyed emittability fixpoint never puts the
 * opted-out callee's symbol in the emitted set, so {@code callsOptedOut} is
 * refused with a diagnostic while {@code callsEmitted} verifies. Verified 3 =
 * foo(int) + callsEmitted + implicit constructor; skipped 2 = the opted-out
 * overload + the dropped caller.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 3, methodsSkipped = 2, errorCount = 0)
final class CallToOptedOutOverload {

    @Verify(false)
    static int foo(@Unbounded int x, @Unbounded int y) {
        return x + y;
    }

    // @Pure + postcondition so callsEmitted's equality is provable: two calls
    // to a non-pure procedure cannot be proven equal.
    @Pure
    @Unbounded
    static int foo(@Unbounded int x) {
        postcondition((int r) -> r == x);
        return x;
    }

    static void callsEmitted(@Unbounded int a) {
        check(foo(a) == a);
    }

    static void callsOptedOut(@Unbounded int a, @Unbounded int b) {
//              ^ error: call to 'foo', which could not be translated
        check(foo(a, b) == foo(a, b));
    }
}

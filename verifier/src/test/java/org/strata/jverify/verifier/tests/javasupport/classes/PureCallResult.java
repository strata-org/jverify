package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * How a caller can use a parameter-dependent {@code @Transparent} method's result. The
 * transparent function inlines at the use site, so the caller sees
 * {@code addOne(a) == a + 1} — provided the relationship to {@code a} survives:
 *
 * <ul>
 *   <li>{@code inCheck} / {@code inPostcondition}: the call sits DIRECTLY in the
 *       assertion/contract, so the inlined body relates result to {@code a} at
 *       once.
 *   <li>{@code viaUnboundedLocal}: via an intermediate local, which works only if
 *       the local carries the same numeric bound as the values it relates. An
 *       unannotated {@code int} local is bounded ({@code int8}); binding an
 *       {@code @Unbounded} result into it would require discharging that bound
 *       (not provable in general), so the local must be {@code @Unbounded} too.
 * </ul>
 *
 * <p>Verified count is 5 (four methods + implicit constructor).
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
final class PureCallResult {

    @Transparent
    @Unbounded
    static int addOne(@Unbounded int x) {
        return x + 1;
    }

    // Directly inside the check: the transparent body inlines here.
    static void inCheck(@Unbounded int a) {
        check(addOne(a) == a + 1);
    }

    // Directly inside the postcondition.
    @Unbounded
    static int inPostcondition(@Unbounded int a) {
        postcondition((int r) -> r == a + 1);
        return addOne(a);
    }

    // Bound to an intermediate local first. The local must be @Unbounded too:
    // an unannotated int is int8, and relating a bounded local to an unbounded
    // value is not provable.
    static void viaUnboundedLocal(@Unbounded int a) {
        @Unbounded int r = addOne(a);
        check(r == a + 1);
    }
}

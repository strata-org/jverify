package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A parameter-dependent {@code @Pure} call result CAN be bound to an intermediate
 * local and then used — provided the local carries the same numeric bound as the
 * values it relates. An unannotated {@code int} local defaults to a bounded type
 * ({@code int8}); binding an {@code @Unbounded} result into it would require
 * discharging that bound (not provable in general), so the local must also be
 * {@code @Unbounded} for {@code r == a + 1} to verify. Annotated, the caller
 * verifies through the local binding.
 *
 * <p>Companion to {@link PureCallResultUsedDirectly} (call used directly, no
 * local). Verified count is 3 (two methods + implicit constructor).
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
final class PureCallResultViaUnboundedLocal {

    @Pure
    @Unbounded
    static int addOne(@Unbounded int x) {
        return x + 1;
    }

    static void viaLocal(@Unbounded int a) {
        // The local must be @Unbounded too: an unannotated int is int8, and
        // relating a bounded local to an unbounded value is not provable.
        @Unbounded int r = addOne(a);
        check(r == a + 1);
    }
}

package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Unbounded;
import org.strata.jverify.Verify;

import static org.strata.jverify.JVerify.*;

/**
 * Companion to {@link VerifyFalseOverload}: the opt-out comes from the CLASS
 * level and one overload opts back in. The resolved (stacked) skip status must
 * feed the overload count, not the method's own annotation.
 */
@Verify(false)
final class VerifyFalseOverloadClassLevel {

    @Verify(true)
    @Unbounded
    static int bar(@Unbounded int x) {
        check(x == x);
        return x;
    }

    // No annotation: inherits the class-level opt-out, emits nothing.
    static int bar(@Unbounded int x, @Unbounded int y) {
        return x + y;
    }
}

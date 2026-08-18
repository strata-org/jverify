package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Refusal reporting is keyed on the method SYMBOL, not a rendering of it.
 *
 * <p>Local classes in different methods may share a name, so {@code owner.toString()}
 * does not identify a class; {@code MethodSymbol.toString()} is
 * {@code name(paramTypes)}, no owner. A string key merges these two {@code f} methods
 * and drops the second refusal -- the silence this reporting exists to remove.
 * Measured before the fix: ONE warning, not two.
 *
 * <p>{@link RefusalDedup} covers the complementary directions (distinct owners at the
 * same signature, one method hitting the same construct twice).
 */
@JVerifyTest(methodsVerified = 5, methodsSkipped = 2, errorCount = 0)
class LocalClassDedup {
    static void first() {
        final class Shared {
            /** Refused: instanceof on an opaque reference type. */
            final boolean f(Object o) { return o instanceof Shared; }
//                        ^ warning: instance method 'f' was not verified: instanceof on opaque reference types is not yet supported
        }
    }

    static void second() {
        final class Shared {
            /** Same name, same signature, different declaration. */
            final boolean f(Object o) { return o instanceof Shared; }
//                        ^ warning: instance method 'f' was not verified: instanceof on opaque reference types is not yet supported
        }
    }
}

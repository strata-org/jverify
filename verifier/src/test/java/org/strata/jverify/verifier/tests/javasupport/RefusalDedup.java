package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An un-verified instance method is reported once per (method, reason), with an
 * owner-qualified dedup key.
 *
 * <p>{@code MethodSymbol.toString()} renders only {@code name(paramTypes)}, without
 * the owner, so keying on it made the two same-signature {@code same()} methods below,
 * in different classes, share a key and dropped the second refusal -- exactly the
 * silence this reporting exists to remove. Measured before the fix: ONE warning
 * instead of two.
 *
 * <p>{@code callThem} is reported at its own declaration, so without the dedup its
 * two refused callees would stack two messages on one position; the key includes the
 * callee, so each distinct callee still gets a line.
 */
@JVerifyTest(exitCode = 2)
class RefusalDedup {
    interface Shape {}

    static class HolderA {
        /** Refused: instanceof on an opaque reference type. */
        final boolean same(Shape s) { return s instanceof HolderA; }
//                    ^ warning: instance method 'same' was not verified: instanceof on opaque reference types is not yet supported
    }

    static class HolderB {
        /** Same signature, different owner: must warn separately. */
        final boolean same(Shape s) { return s instanceof HolderB; }
//                    ^ warning: instance method 'same' was not verified: instanceof on opaque reference types is not yet supported
    }

    static boolean callThem(HolderA a, HolderB b, Shape s) {
//                 ^ error: call to 'same', which could not be translated
        return a.same(s) || b.same(s);
    }
}

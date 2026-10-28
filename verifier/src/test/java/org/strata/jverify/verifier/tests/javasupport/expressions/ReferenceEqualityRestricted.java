package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Impure;
import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * {@code ==} and {@code !=} on references are restricted to operands whose Java
 * identity the translation keeps.
 *
 * <p>A {@code String}, a boxed primitive and a record translate to Laurel values, where
 * two distinct but equal objects compare equal, so comparing two operands that may hold
 * one -- that type, or a supertype of it such as {@code CharSequence} -- is an error.
 * Comparing against a plain class, or against a value typed {@code @Impure} (directly
 * or through a type variable's bound), stays a reference comparison and is allowed.
 */
@JVerifyTest(exitCode = 2)
class ReferenceEqualityRestricted {
    record Point(int x) {
    }

    static class Holder {
        int v;
    }

    @Transparent
    static boolean strings(String a, String b) {
        return a == b;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }

    @Transparent
    static boolean records(Point a, Point b) {
        return a != b;
//               ^ error: '!=' is only allowed when at least one operand's type is impure
    }

    @Transparent
    static boolean supertypeOfString(CharSequence a, String b) {
        return a == b;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }

    @Transparent
    static boolean plainClass(Holder a, Object b) {
        return a == b;
    }

    @Transparent
    static boolean impureObject(@Impure Object a, Object b) {
        return a == b;
    }

    @Transparent
    static <T extends @Impure Object> boolean impureBound(T a, T b) {
        return a == b;
    }

    /** Opted out of verification: the comparison refuses the method silently. */
    @Verify(false)
    static boolean optedOut(String a, String b) {
        return a == b;
    }

    /** {@code a} reuses the slot of the dead {@code @Impure} local {@code o}, but is not {@code @Impure}. */
    static boolean slotReuse(String x, String y) {
        {
            @Impure Object o = x;
            boolean same = o == y;
        }
        String a = x;
        return a == y;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }

    static class Shadow<T extends @Impure Object> {
        /** The method's own {@code T} shadows the class's {@code @Impure}-bounded one. */
        <T> boolean shadowed(T a, T b) {
            return a == b;
//                   ^ error: '==' is only allowed when at least one operand's type is impure
        }

        /** A static method does not see the class's type parameters. */
        static <T> boolean staticShadowed(T a, T b) {
            return a == b;
//                   ^ error: '==' is only allowed when at least one operand's type is impure
        }
    }
}

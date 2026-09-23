package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.*;

/**
 * Type parameters here are used only AS THEMSELVES, so no bound is needed and
 * {@link Blocker#STRATA_NO_TYPE_CONSTRAINTS} does not apply. The case that DID need
 * one -- {@code <T> void objectIsTop(T value) { Object o = value; }}, which relies on
 * the implicit {@code T extends Object} -- moved to {@code PolymorphismWithBounds},
 * where it sits with the explicit bounds that need the same missing feature. Java has
 * no truly unbounded type variable, so "WithoutBounds" is only about the SYNTAX.
 *
 * <p>Measured: {@code <T> T identity(T)} plus {@code check(o == identity(o))}
 * verifies today, which is why the remaining methods are not bound-blocked.
 *
 * <p>What DOES still block it is one line -- the RAW {@code new HasGeneric()} -- which
 * hits {@link Blocker#STRATA_MONOMORPHIZE_BUG}. Everything else here verifies,
 * including {@code new GenericContainer<DummySuper>(..)} and the diamond form. The
 * previous {@code STRATA_GENERIC_OVERRIDE} label was wrong: this file contains no
 * override of a generic method at all (no interface, no {@code @Override}).
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_MONOMORPHIZE_BUG}, methodsVerified = 9, errorCount = 0)
public class PolymorphismWithoutBounds {

    public static void root() {
        // Use a dummy class because we can't currently soundly translate "==" when both operands are of type Object.
        var obj = new Dummy();
        GenericContainer<DummySuper> container = new GenericContainer<DummySuper>(obj);
        check(obj == container.getValue());

        GenericContainer<DummySuper> container2 = new GenericContainer<>(obj);
        check(obj == container2.getValue());

        var obj2 = PolymorphismWithoutBounds.<DummySuper>genericIdentity(obj);
        check(obj == obj2);

        var obj3 = genericIdentity(obj);
        check(obj == obj3);
    }

    @Pure
    public static <T> T genericIdentity(T a) {
        return a;
    }

    static class DummySuper { }
    static class Dummy extends DummySuper {}

    static class GenericContainer<T> {
        private T value;

        public GenericContainer(T value) {
            postcondition(this.value == value);
            this.value = value;
        }

        @Pure
        public T getValue() {
            reads(this);
            return value;
        }
    }

    static class HasGeneric<T> { }

    public void rawGenericClass() {
        var x = new HasGeneric();
    }
}

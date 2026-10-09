package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A generic class whose constructor takes NO arguments, so its instantiation cannot
 * be solved from constructor arguments.
 *
 * <p>This is the case that distinguishes the two sources a frontend can use. A
 * frontend reading the source tree sees {@code <Thing>} directly. From bytecode it
 * cannot: the {@code new} instruction names the raw class and the constructor
 * descriptor is {@code ()V}, so there is nothing to unify against. The instantiation
 * survives only on the local it is stored into, in the LocalVariableTypeTable.
 *
 * <p>Non-vacuous by construction: instantiations are monomorphised, so
 * {@code Holder<Thing>} and {@code Holder<Object>} become DIFFERENT sorts. A
 * frontend that gave up and approximated the argument as {@code Object} would emit
 * an allocation of the wrong sort, and the declaration below would not type check.
 * Both declarations are needed — one alone could be matched by a frontend that
 * guessed a single instantiation for every use of {@code Holder}.
 *
 * <p>Verified 5: the three implicit constructors plus the two methods.
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
public class GenericNoArgConstructor {

    static class Thing {
        int n;
    }

    static class Holder<T> {
        T item;
    }

    static void twoInstantiations() {
        Holder<Thing> things = new Holder<>();
        Holder<Integer> ints = new Holder<>();
        use(things);
        use(ints);
    }

    static <U> void use(Holder<U> h) {
    }
}

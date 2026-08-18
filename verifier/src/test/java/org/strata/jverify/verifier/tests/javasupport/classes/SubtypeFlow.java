package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A value of a subtype flows to any supertype-typed position, because each
 * composite sort is declared {@code extends} its full Java supertype closure
 * (superclass chain + interfaces, transitively including {@code Object}) and
 * Strata's resolver walks those edges. Regression: without the edges the sorts
 * are unrelated on the lattice and the resolver rejected every such flow, each one
 * already proved by Java's type system ("expected 'Animal', got 'Dog'") — while the method
 * was still reported Verified and Strata aborted at an unattributable location.
 *
 * <p>Covers the four flow positions plus multi-hop chains:
 * <ul>
 *   <li>receiver: {@code d.legs()} binds {@code d} (Dog) to {@code legs}'s
 *       {@code self?} parameter (typed Animal) — a {@code final} method called
 *       through a subtype receiver;
 *   <li>argument: {@code takesAnimal(d)};
 *   <li>return: {@code return d} against a declared {@code Animal} output;
 *   <li>local init: {@code Animal a = d};
 *   <li>deep chain: {@code Creature c = d} (Dog -&gt; Animal -&gt; Creature);
 *   <li>interface: {@code takesPet(d)} (Animal implements Pet);
 *   <li>Object from a class: {@code takesObject(d)};
 *   <li>Object from an INTERFACE-typed value: {@code takesObject(p)} — javac
 *       gives an interface no superclass edge, so its Object parent must be
 *       added explicitly (JLS 4.10.2); regression for the hole where interface
 *       sorts were emitted parentless.
 * </ul>
 * {@code legs()} is {@code final} so its receiver call is monomorphic (passes
 * refuseIfPolymorphicDispatch); {@code @Pure} plus a postcondition proves the
 * receiver-flow check, so a dropped edge fails rather than passing vacuously.
 *
 * <p>Verified 10 = root + takesAnimal + takesPet + takesObject + retFlow +
 * Animal.legs + the four implicit constructors (SubtypeFlow, Creature, Animal,
 * Dog).
 */
@JVerifyTest(methodsVerified = 10, errorCount = 0)
public class SubtypeFlow {

    void root(Dog d) {
        // receiver upcast: d (Dog) binds to legs's self parameter (Animal)
        check(d.legs() == 4);
        // argument, interface, local-init, deep-chain, Object upcasts
        takesAnimal(d);
        takesPet(d);
        takesObject(d);
        Animal a = d;
        Creature c = d;
        check(a == a);
        check(c == c);
    }

    static void takesAnimal(Animal a) { }

    static void takesPet(Pet p) {
        // interface-typed value to Object
        takesObject(p);
    }

    static void takesObject(Object o) { }

    static Animal retFlow(Dog d) {
        return d;
    }
}

interface Pet { }

class Creature { }

class Animal extends Creature implements Pet {
    @Pure
    @Unbounded
    final int legs() {
        postcondition((int r) -> r == 4);
        return 4;
    }
}

final class Dog extends Animal { }

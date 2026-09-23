package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.EmptyContract;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NO_TYPE_CONSTRAINTS}, methodsVerified = 16, errorCount = 0)
public class PolymorphismWithBounds {
    
    public static void root() {
        var dog = new Dog();
        var cat = new Cat();
        PetContainer<Dog> container = new PetContainer<Dog>(dog);
        container.doFeed();

        PetContainer<Cat> container2 = new PetContainer<Cat>(cat);
        container2.doFeed();

        feedAndSocialize(dog);
    }
    
    static <T extends Canid & Pet> void feedAndSocialize(T a) {
        a.feed();
        a.socialize();
        takesAPet(a);
    }
    
    static void takesAPet(Pet pet) {
        pet.feed();
    }
    
    static <T extends GenericI<T>> void genericBound(T t) {
        t.foo();
    }

    // The IMPLICIT bound: `<T>` with no extends clause still means `T extends
    // Object`, and JVerify models Object as a composite sort, so this assignment
    // relies on `T <: Object` exactly as the explicit bounds above rely on theirs.
    // Laurel's typeArgs is a list of bare names with no bound slot, so it cannot be
    // expressed: `Impossible to unify Composite with $__opaque_..._T_0`.
    public static <T> void objectIsTop(T value) {
        Object o = value;
    }
}

interface GenericI<T> {
    @EmptyContract
    void foo();
}

class PetContainer<T extends Pet> {
    private T value;

    public PetContainer(T value) {
        postcondition(this.value == value);
        this.value = value;
    }

    public void doFeed() {
        value.feed();
    }
}

interface Pet {
    @EmptyContract
    void feed();
}

interface Canid {
    @EmptyContract
    void socialize();
}

class Dog implements Pet, Canid {
    @Override
    public void feed() {
    }

    @Override
    public void socialize() {
    }
}

class Cat implements Pet {
    @Override
    public void feed() {
    }
}

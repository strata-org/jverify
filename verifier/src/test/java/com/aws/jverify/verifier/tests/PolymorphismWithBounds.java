package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 8, dafnyErrors = 0)
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
}

interface GenericI<T> {
    void foo();

    @Contract
    class GenericIContract<U> implements GenericI<U> {
        @Override
        public void foo() {
            
        }
    }
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

@Contract(Pet.class)
class PetContract implements Pet {
    public void feed() {
    }
}

interface Pet {
    void feed();
}

@Contract(Canid.class)
class CanidContract implements Canid {
    public void socialize() {
    }
}

interface Canid {
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
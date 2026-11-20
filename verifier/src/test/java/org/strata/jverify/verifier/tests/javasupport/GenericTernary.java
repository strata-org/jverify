package org.strata.jverify.verifier.tests.javasupport;

import static org.strata.jverify.JVerify.check;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 13, errorCount = 1)
class GenericTernary {

    static class Box<T> {
        T v;

        T get() {
            return v;
        }
    }

    static <T> T pick(boolean c, Box<T> a, Box<T> b) {
        return c ? a.get() : b.get();
    }

    static <T> T id(T x) {
        return x;
    }

    static Object pickStatic(boolean c, Object a, Object b) {
        return c ? id(a) : id(b);
    }

    static class Animal {
    }

    static class Cat extends Animal {
    }

    static class Dog extends Animal {
    }

    static Animal pickAnimal(boolean c, Cat a, Dog d) {
        return c ? id(a) : id(d);
    }

    static Cat cat() {
        return new Cat();
    }

    static Dog dog() {
        return new Dog();
    }

    static Animal pickDirect(boolean c) {
        return c ? cat() : dog();
    }

    static void bug() {
        check(1 == 2);
//      ^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}

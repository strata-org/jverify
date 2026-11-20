package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 8)
class GenericReceiverTernary<T> {

    static class Animal {
    }

    static class Cat extends Animal {
    }

    static Cat cat() {
        return new Cat();
    }

    Animal animal() {
        return new Animal();
    }

    Animal pick(boolean c) {
        return c ? cat() : animal();
    }

    int f;

    static int three() {
        return 3;
    }

    int fieldOrCall(boolean c) {
        return c ? this.f : three();
    }
}

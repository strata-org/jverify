package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(javaVerified = 10, javaErrors = 0)
public class PolymorphismWithoutBounds {

    public static <T> void objectIsTop(T value) {
        Object o = value;
    }

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

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Immutable;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 6, dafnyErrors = 0)
public class PolymorphismWithoutBounds {

    public static <T> void valueObjectIsTop(T value) {
        @Immutable Object o = value;
    }
    
    public static void root() {
        // Use a dummy class because we can't currently soundly translate "==" when both operands are of type Object.
        var obj = new Dummy();
        GenericContainer<DummySuper> container = new GenericContainer<DummySuper>(obj);
        check(obj == container.getValue());

        GenericContainer<DummySuper> container2 = new GenericContainer<>(obj);
        check(obj == container2.getValue());

        var container3 = new GenericContainer.NestedGenericContainer<>(obj);
        check(obj == container3.getValue());
        
        var obj2 = PolymorphismWithoutBounds.<DummySuper>genericIdentity(obj);
        check(obj == obj2);

        var obj3 = genericIdentity(obj);
        check(obj == obj3);
    }
    
    @Pure
    public static <T> T genericIdentity(T a) {
        return a;
    }
}

class DummySuper { }
class Dummy extends DummySuper {}

class GenericContainer<T> {
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
    
    public static class NestedGenericContainer<U> {
        private U value;

        public NestedGenericContainer(U value) {
            postcondition(this.value == value);
            this.value = value;
        }

        @Pure
        public U getValue() {
            reads(this);
            return value;
        }
    }
}
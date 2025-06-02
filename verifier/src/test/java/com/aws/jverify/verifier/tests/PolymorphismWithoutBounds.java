package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 5, dafnyErrors = 0)
public class PolymorphismWithoutBounds {
    public static void root() {
        var obj = new Object();
        GenericContainer<Object> container = new GenericContainer<Object>(obj);
        check(obj == container.getValue());

        GenericContainer<Object> container2 = new GenericContainer<>(obj);
        check(obj == container2.getValue());

        var container3 = new GenericContainer.NestedGenericContainer<>(obj);
        check(obj == container3.getValue());
        
        var obj2 = PolymorphismWithoutBounds.<Object>genericIdentity(obj);
        check(obj == obj2);

        var obj3 = genericIdentity(obj);
        check(obj == obj3);
    }
    
    @Pure
    public static <T> T genericIdentity(T a) {
        return a;
    }
}

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
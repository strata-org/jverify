package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

// TODO: Support type bounds
// TODO: Support wildcards
@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0)
public class Polymorphism {
    public static void root() {
        var obj = new Object();
        // TODO: support inferred type argument
        // GenericContainer<Object> container = new GenericContainer<>(obj);
        GenericContainer<Object> container = new GenericContainer<Object>(obj);
        check(obj == container.getValue());
        var obj2 = genericIdentity(obj);
        check(obj == obj2);
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
}
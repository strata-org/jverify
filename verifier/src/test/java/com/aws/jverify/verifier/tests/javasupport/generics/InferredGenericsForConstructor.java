package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 7, dafnyErrors = 1)
public class InferredGenericsForConstructor {
    record Value() {}
    static class GenericClass<T> {
        GenericClass(T t) {}
    }
    
    record GenericRecord<T>(T value) {}
    
    void test() {
        var ig = new GenericClass<>(new Value());
        var two = identity(new GenericRecord<>(new Value()));
    }

    <T> T identity(T value) {
        return value;
    }
}

package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 3, errorCount = 0)
public class InferredGenericsForConstructor {
    record Value() {}
    static class GenericClass<T> {
        GenericClass(T t) {}
    }
    
    record GenericRecord<T>(T value) {}
    
    void test() {
//       ^ warning: instance method 'test' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        var ig = new GenericClass<>(new Value());
        var two = identity(new GenericRecord<>(new Value()));
    }

    <T> T identity(T value) {
//        ^ warning: instance method 'identity' was not verified: Unsupported type: T
        return value;
    }
}

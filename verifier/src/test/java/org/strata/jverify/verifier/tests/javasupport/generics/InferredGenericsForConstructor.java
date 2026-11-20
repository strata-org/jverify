package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 5, errorCount = 0)
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

    // A METHOD's own type parameter translates to a Laurel type variable
    // declared in the procedure's typeArgs, so this verifies. A CLASS type
    // parameter still refuses -- see GenContainer in PolymorphicLambdas.
    <T> T identity(T value) {
        return value;
    }
}

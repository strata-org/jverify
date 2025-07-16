package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 22, dafnyErrors = 3, useBuiltinContracts = true, resolvePrintedDafny = true)
public class PolymorphicLambdas {

//    void lambdaForGenericInterfaces() {
//        foo(i -> i < 10 ? i + 2 : i);
//        bar(() -> 10);
//    }

    <U> void lambdaForGenericMethod() {
        zaz((U e) -> 3);
    }

    void foo(MyConsumer<Integer> f) {}
    void bar(MyProducer<Integer> f) {}
    <R> void zaz(MyConsumer<R> f) {}
}

class GenericClass<U> {
    void lambdaForGenericClass() {
        tar((U e) -> 3);    
    }
    
    <R> void tar(MyConsumer<R> f) {}
}

interface MyConsumer<T> {
    int consume(T value);
}

interface MyProducer<T> {
    T produce();
}

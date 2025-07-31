package com.aws.jverify.verifier.tests.expressions.lambdas;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 7, dafnyErrors = 0,
        useBuiltinContracts = false, verifyPrintedDafny = true)
public class PolymorphicLambdas {

    class Anything {}
    void lambdaForGenericInterfaces() {
        foo(i -> 3);
        //noinspection Convert2MethodRef
        bar(() -> new Anything());
    }

    <U> void lambdaForGenericMethod() {
        zaz((U e) -> 3);
    }

    void foo(MyConsumer<Anything> f) {}
    void bar(MyProducer<Anything> f) {}
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

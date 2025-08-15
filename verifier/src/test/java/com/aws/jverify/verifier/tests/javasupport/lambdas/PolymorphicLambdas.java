package com.aws.jverify.verifier.tests.javasupport.lambdas;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

@SuppressWarnings("Convert2MethodRef")
@JVerifyTest(dafnyVerified = 24, dafnyErrors = 0, verifyPrintedDafny = true)
public class PolymorphicLambdas {

    static class Anything {}
    @SuppressWarnings("InnerClassMayBeStatic")
    class InstanceThing {}
    void lambdaForGenericInterfaces() {
        foo(i -> 3);
        fot(i -> 3);
        bar(() -> new Anything());
        bak(() -> new InstanceThing());
    }

    <U> void lambdaForGenericMethod() {
        zaz((U e) -> 3);
        zaz(this::uToThree);
    }
    
    <T> int uToThree(T value) {
        return 3;
    }

    void foo(MyConsumer<Anything> f) {}
    void fot(MyConsumer<InstanceThing> f) {}
    void bar(MyProducer<Anything> f) {}
    void bak(MyProducer<InstanceThing> f) {}
    
    <R> void zaz(MyConsumer<R> f) {}

    static class GenericClass<U> {
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
}

package com.aws.jverify.verifier.tests.localClasses;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 7, dafnyErrors = 0,
        verifyPrintedDafny = true)
public class PolymorphicAnonymousClasses {

    static class Anything {}
    void genericInterfaces() {
        var fooArg = new MyConsumer<Anything>() {
            @Override
            public int consume(Anything value) {
                return 3;
            }
        };
        foo(fooArg);

        var barArg = new MyProducer<Anything>() {
            @Override
            public Anything produce() {
                return new Anything();
            }
        };
        bar(barArg);
    }

    <U> void genericMethod() {
        var zazArg = new MyConsumer<U>() {
            @Override
            public int consume(U value) {
                return 3;
            }
        };
        zaz(zazArg);
    }

    void foo(MyConsumer<Anything> f) {}
    void bar(MyProducer<Anything> f) {}
    <R> void zaz(MyConsumer<R> f) {}

    static class GenericClass<U> {
        
        void genericClass() {
            var tarArg = new MyConsumer<U>() {
                @Override
                public int consume(U value) {
                    return 3;
                }
            };
            tar(tarArg);
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

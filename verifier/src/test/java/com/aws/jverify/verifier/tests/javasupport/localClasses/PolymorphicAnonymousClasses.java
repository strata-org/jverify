package com.aws.jverify.verifier.tests.javasupport.localClasses;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

@SuppressWarnings("Convert2Lambda")
@JVerifyTest(dafnyVerified = 42, dafnyErrors = 0, verifyPrintedDafny = true)
public class PolymorphicAnonymousClasses {

    void capturedGenericType(MyConsumer<Anything> consumer, Anything anything) {
        var captureGeneric = new MyProducer<Anything>() {
            @Override
            public Anything produce() {
                // The variable consumer, with a generic type, is captured
                var i = consumer.consume(anything);
                return anything;
            }
        };
    }
    
    @SuppressWarnings("InnerClassMayBeStatic")
    class InstanceAnything {}
    static class Anything {}
    void genericInterfaces() {
        var fooArg = new MyConsumer<Anything>() {
            @Override
            public int consume(Anything value) {
                return 3;
            }
        };
        foo(fooArg);
        var fotArg = new MyConsumer<InstanceAnything>() {
            @Override
            public int consume(InstanceAnything value) {
                return 3;
            }
        };
        fot(fotArg);

        var barArg = new MyProducer<Anything>() {
            @Override
            public Anything produce() {
                return new Anything();
            }
        };
        bar(barArg);
        var batArg = new MyProducer<InstanceAnything>() {
            @Override
            public InstanceAnything produce() {
                return new InstanceAnything();
            }
        };
        bat(batArg);
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
    void fot(MyConsumer<InstanceAnything> f) {}
    void bar(MyProducer<Anything> f) {}
    void bat(MyProducer<InstanceAnything> f) {}
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
        
        @Contract
        class MyContract<T> implements MyConsumer<T> {
            @Override
            public int consume(T value) {
                throw new ContractException();
            }
        }
    }

    interface MyProducer<T> {
        T produce();
    }
}

package com.aws.jverify.verifier.tests.javasupport.lambdas;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.Supplier;

@SuppressWarnings("Convert2MethodRef")
@JVerifyTest(dafnyVerified = 50, dafnyErrors = 0, verifyPrintedDafny = true)
public class PolymorphicLambdas {

    static class GenContainer<T> {
        void add(T value) {}
    }

    void genTypeInsideLambda(Anything anything) {
        var result = new GenContainer<Anything>();
        result.add(anything);
        foo(_ -> {
            result.add(anything);
            return 3;
        });
    }

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
    void bar(Supplier<Anything> f) {}
    void bak(Supplier<InstanceThing> f) {}

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

    @Contract
    static class SupplierContract<T> implements Supplier<T> {

        @Override
        public T get() {
            throw new ContractException();
        }
    }

    <T> void useTakesHasGeneric(Supplier<GenericClass<T>> supplier) {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void rawGenericClassInLambda() {
        useTakesHasGeneric(() -> new GenericClass<>());
        useTakesHasGeneric(() -> new GenericClass());
        useTakesHasGeneric(GenericClass::new);
    }

    public <T> void genericLambda() {
        useTakesHasGeneric(() -> new GenericClass<T>());
    }

}

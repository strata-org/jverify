package org.strata.jverify.verifier.tests.javasupport.lambdas;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import java.util.function.Supplier;

@SuppressWarnings("Convert2MethodRef")
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_GENERIC_OVERRIDE}, methodsVerified = 18, errorCount = 0)
public class PolymorphicLambdas {

    static class GenContainer<T> {
        void add(T value) {}
//           ^ warning: instance method 'add' was not verified: Unsupported type: T
    }

    void genTypeInsideLambda(Anything anything) {
//       ^ warning: instance method 'genTypeInsideLambda' was not verified: call to 'add', which could not be translated
        var result = new GenContainer<Anything>();
        result.add(anything);
        foo(_ -> {
//          ^ warning: instance method 'consume' was not verified: call to 'add', which could not be translated
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

    // uToThree declares T ITSELF, so T becomes a Laurel type variable in the
    // procedure's typeArgs and the method verifies -- unlike GenContainer.add
    // above, whose T belongs to the CLASS and still refuses.
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
//          ^ warning: instance method 'consume' was not verified: Unsupported type: T
    }

    @Contract
    static class SupplierContract<T> implements Supplier<T> {

        @Override
        public T get() {
//               ^ warning: instance method 'get' was not verified: Unsupported type: T
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

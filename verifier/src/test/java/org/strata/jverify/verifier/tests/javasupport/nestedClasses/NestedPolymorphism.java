package org.strata.jverify.verifier.tests.javasupport.nestedClasses;

import org.strata.jverify.Pure;
import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 4, errorCount = 0)
public class NestedPolymorphism {

    static class DummySuper { }
    static class Dummy extends DummySuper {}
    
    static class OuterGenericClass<T> {
        NestedGenericContainer<Dummy> nestedGenericContainer;
        NestedGenericContainer<T> nestedGenericContainerT;

        public OuterGenericClass(T value) {
            var dummy = new Dummy();
            nestedGenericContainer = new NestedGenericContainer<>(dummy);
            nestedGenericContainerT = new NestedGenericContainer<>(value);
            
            check(nestedGenericContainer.getValue() == dummy); 
        }

        // `U extends @Impure Object` is load bearing: the postcondition below wants
        // REFERENCE identity, and an unconstrained U may be instantiated at a record or
        // String, which translate to Laurel VALUES -- so `==` would silently compare
        // values, not references. The bound pins U to a heap object.
        public static class NestedGenericContainer<U extends @Impure Object> {
            private U value;

            public NestedGenericContainer(U value) {
                postcondition(this.value == value);
                this.value = value;
            }

            @Pure
            public U getValue() {
                reads(this);
                return value;
            }
        }
    }
}

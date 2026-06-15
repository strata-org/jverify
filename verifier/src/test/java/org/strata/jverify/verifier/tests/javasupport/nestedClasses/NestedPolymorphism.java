package org.strata.jverify.verifier.tests.javasupport.nestedClasses;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 5, errorCount = 0)
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

        public static class NestedGenericContainer<U> {
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

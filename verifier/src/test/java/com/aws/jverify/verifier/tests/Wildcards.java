package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0)
public class Wildcards {
    
    boolean isNull(Container<?> elements) {
        return elements.alwaysTrue();
    }
    
    void numberGetterUser(Container<Turtle> dogs) {
        var name = animalNameGetter(dogs);
    }
    
    <T> String animalNameGetter(Container<? extends Animal> animals) {
        return animals.get().name();
    }

    void numberSetterUser(Container<Object> objects, Turtle dog) {
        animalSetter(objects, dog);
    }
    
    <T> void animalSetter(Container<? super Animal> numberContainer, Turtle d) {
        numberContainer.sett(d);
    }

    static void alwaysTruerUser(Container<Object> container) {
        alwaysTruer(container);
    }
    
    static void alwaysTruer(Container<?> container) {
        var x = container.alwaysTrue();
    }

    static class Container<T> {
        T value;

        @Pure
        public T get() {
            return value;
        }

        public void sett(T value) {
            this.value = value;
        }
        
        public boolean alwaysTrue() {
            return true;
        }

    }

    interface Animal {
        String name();

        @Contract
        class MyContract implements Animal {

            @Override
            public String name() {
                throw new ContractException();
            }
        }
    }
    interface Turtle extends Animal {}
}

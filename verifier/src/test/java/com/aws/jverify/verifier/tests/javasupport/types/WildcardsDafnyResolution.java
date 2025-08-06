package com.aws.jverify.verifier.tests.javasupport.types;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22, useBuiltinContracts = true)
public class WildcardsDafnyResolution {
    
    boolean isNull(Container<?> elements) {
        return elements.alwaysTrue();
    }
    
    void numberGetterUser(Container<Turtle> dogs) {
        var name = animalNameGetter(dogs);
//                                  ^^^^ Error: incorrect argument type for method in-parameter 'animals' (expected Container<Animal>, found Container<Turtle>) (non-variant type parameter 'T' would require Animal = Turtle)
    }
    
    <T> String animalNameGetter(Container<? extends Animal> animals) {
        return animals.get().name();
    }

    static void innerSuperUsage(Container<Object> objects, Turtle dog) {
        Container<? super Animal> animals = objects;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Container<Object>) not assignable to LHS (of type Container<Animal>) (non-variant type parameter 'T' would require Animal = Object)
        animals.sett(dog);
    }

    void animalSetterUser(Container<Object> objects, Turtle dog) {
        animalSetter(objects, dog);
//                   ^^^^^^^ Error: incorrect argument type at index 0 for method in-parameter 'animals' (expected Container<Animal>, found Container<Object>) (non-variant type parameter 'T' would require Animal = Object)
    }

    @Verify(false)
    <T> void animalSetter(Container<? super Animal> animals, Turtle d) {
        animals.sett(d);
    }
    
    static void alwaysTruerUser(Container<Animal> animals, Container<Object> objects) {
        alwaysTruer(animals);
//                  ^^^^^^^ Error: incorrect argument type for method in-parameter 'container' (expected Container<Object>, found Container<Animal>) (non-variant type parameter 'T' would require Object = Animal)
        alwaysTruer(objects);
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

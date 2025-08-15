package com.aws.jverify.verifier.tests.javasupport.wildcards;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class WildcardsNotSupported {
    
    void animalSetterUser(Container<Object> objects, Turtle dog) {
        animalSetter(objects, dog);
    }
    
    void animalSetter(Container<? super Animal> animals, Turtle d) {
//                    ^ error: keyword 'super' in method signature is not supported
        animals.sett(d);
    }

    Container<? super Animal> superReturnType() {
//  ^ error: keyword 'super' in method signature is not supported
        return null;
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

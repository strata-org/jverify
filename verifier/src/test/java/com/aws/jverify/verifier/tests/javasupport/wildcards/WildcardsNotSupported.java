package com.aws.jverify.verifier.tests.javasupport.wildcards;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.modifies;
import static com.aws.jverify.JVerify.reads;

@JVerifyTest(exitCode = 0, dafnyVerified = 10, dafnyErrors = 0)
public class WildcardsNotSupported {
    
    void animalSetterUser(Container<Animal> animals, Turtle dog) {
        modifies(animals);
        animalSetter(animals, dog);
    }
    
    void animalSetter(Container<? super Animal> animals, Turtle d) {
        modifies(animals);
        animals.sett(d);
    }

    Container<? super Animal> superReturnType() {
        return new Container<Animal>();
    }

    static class Container<T> {
        T value;

        @Pure
        public T get() {
            reads(this);
            return value;
        }

        public void sett(T value) {
            modifies(this);
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

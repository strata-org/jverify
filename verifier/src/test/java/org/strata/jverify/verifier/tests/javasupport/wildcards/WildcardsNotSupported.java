package org.strata.jverify.verifier.tests.javasupport.wildcards;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import static org.strata.jverify.JVerify.modifies;
import static org.strata.jverify.JVerify.reads;

@JVerifyTest(exitCode = 0, methodsVerified = 4, errorCount = 0)
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

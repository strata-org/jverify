package org.strata.jverify.verifier.tests.javasupport.wildcards;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import static org.strata.jverify.JVerify.modifies;
import static org.strata.jverify.JVerify.reads;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_WILDCARDS}, exitCode = 0, methodsVerified = 4, errorCount = 0)
public class WildcardsNotSupported {
    
    void animalSetterUser(Container<Animal> animals, Turtle dog) {
//       ^ warning: instance method 'animalSetterUser' was not verified: call to 'animalSetter', which could not be translated
        modifies(animals);
        animalSetter(animals, dog);
    }

    // Not a generics gap: a class type parameter IS translated now. The wildcard
    // approximation is what blocks this. `? super Animal` is approximated by its read
    // type Object, so this parameter becomes Container<Object>, and the caller above
    // passes a Container<Animal> -- measured as
    // "expected 'Container<java.lang.Object>', got 'Container<Animal>'". Generic
    // instantiations are monomorphised and invariant, so no approximation of a
    // wildcard by a single type can satisfy both the declaration and its call sites.
    // Giving a wildcard real meaning needs variance in the target language, which is
    // why this stays STRATA_WILDCARDS.
    void animalSetter(Container<? super Animal> animals, Turtle d) {
//       ^ warning: instance method 'animalSetter' was not verified: call to 'sett', which could not be translated
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
//               ^ warning: instance method 'get' was not verified: Unsupported type: T
            reads(this);
            return value;
        }

        public void sett(T value) {
//                  ^ warning: instance method 'sett' was not verified: Unsupported type: T
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

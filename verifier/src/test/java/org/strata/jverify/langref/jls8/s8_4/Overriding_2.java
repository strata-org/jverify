package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.8.1 -- Overriding (by Instance Methods): a subclass instance method
// overrides a superclass instance method with the same signature, and the
// overriding method is selected dynamically based on the runtime type. The
// superclass version remains reachable through a super invocation.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.Overriding_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Overriding_2 {
    static void run() {

        Animal_Overriding_2 a = new Dog_Overriding_2();
        // Dynamic dispatch selects the overriding method on the runtime type.

        // super.sound() reaches the overridden superclass method.
        check(((Dog_Overriding_2) a).baseSound() == 1);
    }
}

class Animal_Overriding_2 {
    int sound() {
        return 1;
    }
}

class Dog_Overriding_2 extends Animal_Overriding_2 {
    @Override
    int sound() {
        return 2;
    }

    int baseSound() {
        return super.sound();
    }
}

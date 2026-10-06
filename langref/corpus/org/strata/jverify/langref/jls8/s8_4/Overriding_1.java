package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.8.1 -- Overriding (by Instance Methods): a subclass instance method
// overrides a superclass instance method with the same signature, and the
// overriding method is selected dynamically based on the runtime type. The
// superclass version remains reachable through a super invocation.
class Overriding_1 {
    static void run() {

        Animal_Overriding_1 a = new Dog_Overriding_1();
        // Dynamic dispatch selects the overriding method on the runtime type.
        check(a.sound() == 2);
    }
}

class Animal_Overriding_1 {
    int sound() {
        return 1;
    }
}

class Dog_Overriding_1 extends Animal_Overriding_1 {
    @Override
    int sound() {
        return 2;
    }

    int baseSound() {
        return super.sound();
    }
}

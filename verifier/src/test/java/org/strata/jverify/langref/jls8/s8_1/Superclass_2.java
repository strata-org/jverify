package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.4 -- Superclasses and Subclasses: an "extends" clause names a direct
// superclass; the subclass inherits members and may override methods.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.Superclass_2#run")
class Superclass_2 {
    static void run() {

        Animal_Superclass_2 a = new Dog_Superclass_2();

        check(a.baseLegs() == 2);
    }
}

class Animal_Superclass_2 {
    int legs() {
        return 2;
    }

    int baseLegs() {
        return 2;
    }
}

class Dog_Superclass_2 extends Animal_Superclass_2 {
    @Override
    int legs() {
        return 4;
    }
}

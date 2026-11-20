package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.4 -- Superclasses and Subclasses: an "extends" clause names a direct
// superclass; the subclass inherits members and may override methods.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.Superclass_1#run")
class Superclass_1 {
    static void run() {

        Animal_Superclass_1 a = new Dog_Superclass_1();
        check(a.legs() == 4);
    }
}

class Animal_Superclass_1 {
    int legs() {
        return 2;
    }

    int baseLegs() {
        return 2;
    }
}

class Dog_Superclass_1 extends Animal_Superclass_1 {
    @Override
    int legs() {
        return 4;
    }
}

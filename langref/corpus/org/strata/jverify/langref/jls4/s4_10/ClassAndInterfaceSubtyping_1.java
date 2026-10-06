package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.2 -- Subtyping among Class and Interface Types: a subclass/implementor
// is a subtype of its direct superclass and superinterfaces; subtyping is
// reflexive and transitive. Exercised via widening reference conversions.
class ClassAndInterfaceSubtyping_1 {
    static void run() {

        Dog_ClassAndInterfaceSubtyping_1 dog = new Dog_ClassAndInterfaceSubtyping_1();
        // Dog_ClassAndInterfaceSubtyping_1 <: Animal_ClassAndInterfaceSubtyping_1 (direct superclass), transitively Dog_ClassAndInterfaceSubtyping_1 <: Object.
        Animal_ClassAndInterfaceSubtyping_1 a = dog;

        check(a.legs() == 4);
    }
}

interface Named_ClassAndInterfaceSubtyping_1 {
    int name();
}

class Animal_ClassAndInterfaceSubtyping_1 {
    int legs() { return 4; }
}

class Dog_ClassAndInterfaceSubtyping_1 extends Animal_ClassAndInterfaceSubtyping_1 implements Named_ClassAndInterfaceSubtyping_1 {
    public int name() { return 1; }
}

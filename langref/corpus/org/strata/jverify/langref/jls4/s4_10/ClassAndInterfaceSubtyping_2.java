package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.2 -- Subtyping among Class and Interface Types: a subclass/implementor
// is a subtype of its direct superclass and superinterfaces; subtyping is
// reflexive and transitive. Exercised via widening reference conversions.
class ClassAndInterfaceSubtyping_2 {
    static void run() {

        Dog_ClassAndInterfaceSubtyping_2 dog = new Dog_ClassAndInterfaceSubtyping_2();
        // Dog_ClassAndInterfaceSubtyping_2 <: Named_ClassAndInterfaceSubtyping_2 (implemented interface).
        Named_ClassAndInterfaceSubtyping_2 n = dog;
        check(n.name() == 1);
    }
}

interface Named_ClassAndInterfaceSubtyping_2 {
    int name();
}

class Animal_ClassAndInterfaceSubtyping_2 {
    int legs() { return 4; }
}

class Dog_ClassAndInterfaceSubtyping_2 extends Animal_ClassAndInterfaceSubtyping_2 implements Named_ClassAndInterfaceSubtyping_2 {
    public int name() { return 1; }
}

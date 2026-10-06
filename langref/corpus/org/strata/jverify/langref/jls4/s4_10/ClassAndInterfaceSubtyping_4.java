package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.2 -- Subtyping among Class and Interface Types: a subclass/implementor
// is a subtype of its direct superclass and superinterfaces; subtyping is
// reflexive and transitive. Exercised via widening reference conversions.
class ClassAndInterfaceSubtyping_4 {
    static void run() {

        Dog_ClassAndInterfaceSubtyping_4 dog = new Dog_ClassAndInterfaceSubtyping_4();
        // Dog_ClassAndInterfaceSubtyping_4 <: Named_ClassAndInterfaceSubtyping_4 (implemented interface).
        Named_ClassAndInterfaceSubtyping_4 n = dog;

        // Interface subtyping: Named_ClassAndInterfaceSubtyping_4 is itself a subtype of Object.
        Object on = n;
        check(on != null);
    }
}

interface Named_ClassAndInterfaceSubtyping_4 {
    int name();
}

class Animal_ClassAndInterfaceSubtyping_4 {
    int legs() { return 4; }
}

class Dog_ClassAndInterfaceSubtyping_4 extends Animal_ClassAndInterfaceSubtyping_4 implements Named_ClassAndInterfaceSubtyping_4 {
    public int name() { return 1; }
}

package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.2 -- Variables of Reference Type: a reference-typed variable holds
// either a reference to an object (of its type or a subtype) or null.
class ReferenceTypeVariable_3 {
    static void run() {

        Animal_ReferenceTypeVariable_3 a = new Dog_ReferenceTypeVariable_3(7);                // may hold null

        a = null;
        check(a == null);
    }
}

class Animal_ReferenceTypeVariable_3 {
    int legs;
    Animal_ReferenceTypeVariable_3(int legs) { this.legs = legs; }
    int legs() { return legs; }
}

class Dog_ReferenceTypeVariable_3 extends Animal_ReferenceTypeVariable_3 {
    Dog_ReferenceTypeVariable_3(int legs) { super(legs); }
}

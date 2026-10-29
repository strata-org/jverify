package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.2 -- Subtyping among Class and Interface Types: a subclass/implementor
// is a subtype of its direct superclass and superinterfaces; subtyping is
// reflexive and transitive. Exercised via widening reference conversions.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.ClassAndInterfaceSubtyping_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ClassAndInterfaceSubtyping_3 {
    static void run() {

        Dog_ClassAndInterfaceSubtyping_3 dog = new Dog_ClassAndInterfaceSubtyping_3();
        Object o = dog;
        check(o != null);
    }
}

interface Named_ClassAndInterfaceSubtyping_3 {
    int name();
}

class Animal_ClassAndInterfaceSubtyping_3 {
    int legs() { return 4; }
}

class Dog_ClassAndInterfaceSubtyping_3 extends Animal_ClassAndInterfaceSubtyping_3 implements Named_ClassAndInterfaceSubtyping_3 {
    public int name() { return 1; }
}

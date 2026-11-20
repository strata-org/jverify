package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.8.2 -- Hiding (by Class Methods): a static method in a subclass with
// the same signature as a static method in the superclass HIDES (not overrides)
// it. Static method invocation is resolved by the compile-time type, so the
// hiding method and hidden method are reached based on the static type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.Hiding_3#run")
class Hiding_3 {
    static void run() {

        // The hidden superclass static is still reachable as Parent_Hiding_3.kind().
        check(Child_Hiding_3.viaParent() == 1);
    }
}

class Parent_Hiding_3 {
    static int kind() {
        return 1;
    }
}

class Child_Hiding_3 extends Parent_Hiding_3 {
    // This static method hides Parent.kind() rather than overriding it.
    static int kind() {
        return 2;
    }

    static int viaParent() {
        return Parent_Hiding_3.kind();
    }
}

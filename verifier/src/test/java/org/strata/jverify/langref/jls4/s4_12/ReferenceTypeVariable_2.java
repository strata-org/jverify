package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.2 -- Variables of Reference Type: a reference-typed variable holds
// either a reference to an object (of its type or a subtype) or null.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.ReferenceTypeVariable_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ReferenceTypeVariable_2 {
    static void run() {
          // holds reference to a subtype instance
        Animal_ReferenceTypeVariable_2 n = null;
        check(n == null);
    }
}

class Animal_ReferenceTypeVariable_2 {
    int legs;
    Animal_ReferenceTypeVariable_2(int legs) { this.legs = legs; }
    int legs() { return legs; }
}

class Dog_ReferenceTypeVariable_2 extends Animal_ReferenceTypeVariable_2 {
    Dog_ReferenceTypeVariable_2(int legs) { super(legs); }
}

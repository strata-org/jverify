package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.2 -- The Class Object: every class is a subclass of Object; methods like equals.
class TheClassObject_2 {
    static void run() {

        // Object is the root of the class hierarchy; an Object reference can hold any object.
        Object a = new Object();
        Object b = a;
        // Object.equals is reference equality for plain Object instances.

        check(!a.equals(new Object()));
    }
}

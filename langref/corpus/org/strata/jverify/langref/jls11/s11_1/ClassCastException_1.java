package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ClassCastException when a checked cast to an incompatible
// reference type is performed at runtime.
class ClassCastException_1 {
    static void run() {
        Object o = "hi";
        boolean caught = false;
        try {
            Integer i = (Integer) o;
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);
    }
}

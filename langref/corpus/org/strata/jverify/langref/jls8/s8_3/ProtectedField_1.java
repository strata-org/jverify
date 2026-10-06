package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1 -- protected field access: a protected instance field is inherited
// by subclasses and readable via subclass instances.
class ProtectedField_1 {
    static class B {
        protected int v = 5;
    }

    static class S extends B {
    }

    static void run() {
        check(new S().v == 5);
    }
}

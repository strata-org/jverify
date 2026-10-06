package org.strata.jverify.langref.jls8.s8_8;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.3 -- protected constructor access: a subclass may invoke a superclass
// protected constructor via its own default constructor.
class ProtectedConstructor_1 {
    static class B {
        protected B() {
        }
    }

    static class S extends B {
    }

    static void run() {
        check(new S() instanceof B);
    }
}

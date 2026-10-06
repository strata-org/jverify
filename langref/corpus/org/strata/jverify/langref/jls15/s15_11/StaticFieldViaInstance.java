package org.strata.jverify.langref.jls15.s15_11;

import static org.strata.jverify.JVerify.*;

// JLS 15.11 -- Field Access: a static field accessed through an instance
// reference resolves to the declaring class's static field.
class StaticFieldViaInstance {
    static class C {
        static int SHARED = 42;
    }

    static void run() {

        C c = new C();
        check(c.SHARED == 42);
    }
}

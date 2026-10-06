package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.1 -- static Fields: a static field belongs to the class, not to any
// particular instance; every instance reads and writes the same storage.
class StaticField_SharedAcrossInstances {
    static class C {
        static int shared = 0;
    }

    static void run() {
        C.shared = 0;
        C a = new C();
        C b = new C();
        C.shared = 7;
        check(a.shared == 7 && b.shared == 7);
    }
}

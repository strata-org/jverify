package org.strata.jverify.langref.jls15.s15_11;

import static org.strata.jverify.JVerify.*;

// JLS 15.11 -- Field Access: static field access via the class name.
class StaticFieldViaClass {
    static class C {
        static int SHARED = 42;
    }

    static void run() {

        check(C.SHARED == 42);
    }
}

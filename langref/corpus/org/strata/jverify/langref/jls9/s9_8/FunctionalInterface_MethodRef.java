package org.strata.jverify.langref.jls9.s9_8;

import static org.strata.jverify.JVerify.*;

// JLS 9.8 -- a functional interface may be the target type of a method
// reference; the referenced method supplies the implementation.
class FunctionalInterface_MethodRef {
    interface I {
        int apply(int x);
    }

    static int neg(int x) {
        return -x;
    }

    static void run() {
        I i = FunctionalInterface_MethodRef::neg;
        check(i.apply(5) == -5);
    }
}

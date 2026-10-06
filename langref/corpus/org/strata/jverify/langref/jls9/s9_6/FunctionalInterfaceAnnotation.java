package org.strata.jverify.langref.jls9.s9_6;

import static org.strata.jverify.JVerify.*;

// JLS 9.6.4.9 -- @FunctionalInterface: marks an interface as having exactly one abstract
// method; here it is implemented by a lambda expression and invoked.
class FunctionalInterfaceAnnotation {
    @FunctionalInterface
    interface IntOp {
        int apply(int a, int b);
    }

    static void run() {
        IntOp add = (a, b) -> a + b;
        check(add.apply(2, 3) == 5);
    }
}

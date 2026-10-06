package org.strata.jverify.langref.jls9.s9_8;

import static org.strata.jverify.JVerify.*;

// JLS 9.8 -- Functional Interfaces: an interface with exactly one abstract
// method is a functional interface and may be the target type of a lambda
// expression. Here a self-declared functional interface is instantiated with
// a lambda and invoked.
class FunctionalInterface {

    // A functional interface: exactly one abstract method.
    interface IntBinaryOp {
        int apply(int a, int b);
    }

    static void run() {
        IntBinaryOp add = (a, b) -> a + b;
        int r = add.apply(2, 3);
        check(r == 5);
    }
}

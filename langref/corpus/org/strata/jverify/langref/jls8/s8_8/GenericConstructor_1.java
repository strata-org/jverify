package org.strata.jverify.langref.jls8.s8_8;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.4 -- constructor of a generic class: the constructor of a generic
// class Box<T> takes a T and stores it in a T-typed field.
class GenericConstructor_1 {
    static class Box<T> {
        T v;

        Box(T v) {
            this.v = v;
        }
    }

    static void run() {
        Box<Integer> b = new Box<>(7);
        check(b.v == 7);
    }
}

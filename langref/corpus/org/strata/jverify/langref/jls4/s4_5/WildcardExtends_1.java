package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.1 -- Wildcard with upper bound (? extends Number): a Box<Integer> is
// assignable to Box<? extends Number>; the extracted value is a Number.
class WildcardExtends_1 {
    static void run() {

        Box_WildcardExtends_1<? extends Number> b = new Box_WildcardExtends_1<Integer>(3);
        Number n = b.get();
        check(n.intValue() == 3);
    }

    static class Box_WildcardExtends_1<T> {
        private final T v;
        Box_WildcardExtends_1(T v) { this.v = v; }
        T get() { return v; }
    }
}

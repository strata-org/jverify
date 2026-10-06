package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.1 -- Wildcard with lower bound (? super Integer): an Integer can be
// stored via the ? super Integer view; the underlying Box<Integer> observes the write.
class WildcardSuper_1 {
    static void run() {

        Box_WildcardSuper_1<Integer> bi = new Box_WildcardSuper_1<>(3);
        Box_WildcardSuper_1<? super Integer> bs = bi;
        bs.set(5);
        check(bi.get() == 5);
    }

    static class Box_WildcardSuper_1<T> {
        private T v;
        Box_WildcardSuper_1(T v) { this.v = v; }
        T get() { return v; }
        void set(T v) { this.v = v; }
    }
}

package org.strata.jverify.langref.jls4.s4_9;

import static org.strata.jverify.JVerify.*;

// JLS 4.9 -- Intersection Types: an intersection type also appears directly in a cast
// expression of the form (T1 & T2) e (JLS 15.16 / 5.5). Casting to A & B produces a
// value usable as both A and B, and is the canonical way to give a lambda a type that
// implements several interfaces at once.
class IntersectionTypeCast_2 {
    static void run() {

        Combo c = new Combo();
        // Cast to the intersection type Adder & Doubler: the result is statically known
        // to be both interfaces, so members of each are callable on the same reference.
        Object o = c;
        Adder both = (Adder & Doubler) o;                 // via Adder
        int d = ((Doubler) both).twice(5);
        check(d == 10);
    }

    interface Adder {
        int add(int x, int y);
    }

    interface Doubler {
        int twice(int x);
    }

    static class Combo implements Adder, Doubler {
        public int add(int x, int y) { return x + y; }
        public int twice(int x) { return x * 2; }
    }
}

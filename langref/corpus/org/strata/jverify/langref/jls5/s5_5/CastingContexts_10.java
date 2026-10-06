package org.strata.jverify.langref.jls5.s5_5;

import static org.strata.jverify.JVerify.*;

// JLS 5.5 -- Casting Contexts: conversions permitted by a cast operator (CastExpression).
// Exercises identity, widening/narrowing primitive, widening/narrowing reference,
// boxing, and unboxing conversions in cast position.
class CastingContexts_10 {
    static void run() {

        // Identity conversion via cast
        int a = (int) 5;

        // Boxing conversion via cast: int -> Integer
        Integer boxed = (Integer) a;
        check(boxed.intValue() == 5);
    }

    interface Valued {
        int value();
    }

    static final class Box implements Valued {
        private final int v;
        Box(int v) { this.v = v; }
        public int value() { return v; }
    }
}

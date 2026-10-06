package org.strata.jverify.langref.jls5.s5_5;

import static org.strata.jverify.JVerify.*;

// JLS 5.5 -- Casting Contexts: conversions permitted by a cast operator (CastExpression).
// Exercises identity, widening/narrowing primitive, widening/narrowing reference,
// boxing, and unboxing conversions in cast position.
class CastingContexts_7 {
    static void run() {

        // Widening reference conversion via cast: String -> Object
        String s = "hello";
        Object o = (Object) s;
        check(o == s);
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

package org.strata.jverify.langref.jls5.s5_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.5 -- Casting Contexts: conversions permitted by a cast operator (CastExpression).
// Exercises identity, widening/narrowing primitive, widening/narrowing reference,
// boxing, and unboxing conversions in cast position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_5.CastingContexts_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastingContexts_4 {
    static void run() {

        // Narrowing primitive conversion via cast: int -> byte (wraps)
        int big = 300;
        byte b = (byte) big;     // 300 mod 256 -> 44
        check(b == 44);
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

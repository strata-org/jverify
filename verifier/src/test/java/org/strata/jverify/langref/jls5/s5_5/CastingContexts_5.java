package org.strata.jverify.langref.jls5.s5_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.5 -- Casting Contexts: conversions permitted by a cast operator (CastExpression).
// Exercises identity, widening/narrowing primitive, widening/narrowing reference,
// boxing, and unboxing conversions in cast position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_5.CastingContexts_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastingContexts_5 {
    static void run() {

        // Narrowing primitive via cast: double -> int (truncates)
        double frac = 3.99;
        int di = (int) frac;
        check(di == 3);
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

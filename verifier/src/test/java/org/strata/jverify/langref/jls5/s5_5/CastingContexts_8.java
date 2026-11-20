package org.strata.jverify.langref.jls5.s5_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.5 -- Casting Contexts: conversions permitted by a cast operator (CastExpression).
// Exercises identity, widening/narrowing primitive, widening/narrowing reference,
// boxing, and unboxing conversions in cast position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_5.CastingContexts_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastingContexts_8 {
    static void run() {

        // Narrowing reference conversion via cast: Object -> String (checked cast)
        Object ref = "world";
        String back = (String) ref;
        check(back.length() == 5);
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

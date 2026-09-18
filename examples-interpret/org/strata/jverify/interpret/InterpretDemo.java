package org.strata.jverify.interpret;

import org.strata.jverify.InterpretEntry;

import static org.strata.jverify.JVerify.*;

/**
 * Demonstrates JVerify's --interpret mode.
 *
 * Unlike verification (which reasons about all possible inputs symbolically),
 * interpret mode concretely *executes* the entry point(s) the producer marks
 * with {@code @InterpretEntry}, using the values baked into the source and
 * treating contracts (`check`, `precondition`, ...) as runtime assertions.
 * No --entry flag is needed: the marker travels through to Laurel/Core metadata
 * and the interpreter finds it there.
 *
 * Run the marked entry point:
 *   verifier --interpret InterpretDemo.java
 */
class InterpretDemo {

    /** Marked entry point whose assertions all hold on the concrete input. */
    @InterpretEntry
    static void runOk() {
        int x = 5;
        int y = x * 2 - 3;
        check(y == 7);
    }

    /** A method that is NOT an entry point: it is never executed by --interpret. */
    static void notAnEntry() {
        check(false);
    }
}

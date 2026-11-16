package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An operand of a reference comparison that is a box on one path into a join
 * and another object on the other paths may be a box, whichever path the
 * translation follows first: each comparison is refused. In Java the first and
 * the third check fail and the second holds, because both boxes are the cached
 * {@code Integer} 127.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class InterpretBoxingJoinRefusals {
    static class Point {
    }

    static boolean flag(boolean b) {
        return b;
    }

    static void nestedDiffers() {
        Point p = new Point(), q = new Point();
        Object o = 127, o2 = 127;
        boolean c = flag(true), d = flag(false);
        check((c ? (d ? q : o) : p) != o2);
//            ^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static void nestedSame() {
        Point p = new Point(), q = new Point();
        Object o = 127, o2 = 127;
        boolean c = flag(true), d = flag(false);
        check((c ? (d ? q : o) : p) == o2);
//            ^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static void shortCircuit() {
        Point q = new Point();
        Object o = 127, o2 = 127;
        boolean c = flag(true), d = flag(false);
        check(o2 != (c && d ? o : q));
//            ^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    @InterpretEntry
    static void run() {
        Integer boxed = 1;
        check(boxed == 1);
    }
}

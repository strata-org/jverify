package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call on a library receiver may run the override of any class the
 * program allocates. {@code Point.equals} is refused (it compares
 * {@code double}s), so the method that calls {@code Object.equals} is refused
 * with that reason, at the method: the override is found after the call is
 * translated.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class LibraryOverriderRefusalsCortex {
    static boolean same(Object a, Object b) {
//                 ^^^^ error: the library method 'java.awt.Point.equals' could not be translated: the library method 'java.awt.geom.Point2D.equals' could not be translated: not supported by the direct Cortex translation: floating point
        return a.equals(b);
    }

    @InterpretEntry
    static void run() {
        Object p = new java.awt.Point(1, 2);
        check(same(p, p));
    }
}

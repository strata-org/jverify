package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code Boolean.TRUE} and {@code Boolean.FALSE} are static fields of the
 * contract of {@code Boolean}, which its static initializer assigns once: two
 * objects, each the same object at every read.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        errorCount = 0)
class InterpretBooleanConstants {
    @InterpretEntry
    static void run() {
        Boolean t = Boolean.TRUE;
        check(t == Boolean.TRUE);
        check(t != Boolean.FALSE);
        Object o = Boolean.FALSE;
        check(o instanceof Boolean);
    }
}

package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call on a library receiver may run the override of a program
 * class. The {@code equals} javac generates for a record is not translated,
 * so each method that calls {@code Object.equals} in a program that allocates
 * the record is refused, whether it checks that the call answers true or
 * false, where running {@code Object.equals} would compare the references.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class LibraryUntranslatedOverrideRefusalsCortex {
    record P(int x) {
    }

    static void recordEquals() {
//              ^^^^^^^^^^^^ error: the library method 'org.strata.jverify.verifier.tests.javasupport.library.LibraryUntranslatedOverrideRefusalsCortex.P.equals' could not be translated: not supported by the direct Cortex translation: the equals javac generates for a record
        Object a = new P(1);
        check(a.equals(new P(1)));
    }

    static void recordDiffers() {
//              ^^^^^^^^^^^^^ error: the library method 'org.strata.jverify.verifier.tests.javasupport.library.LibraryUntranslatedOverrideRefusalsCortex.P.equals' could not be translated: not supported by the direct Cortex translation: the equals javac generates for a record
        Object a = new P(1);
        check(!a.equals(new P(1)));
    }

    @InterpretEntry
    static void run() {
        recordEquals();
        recordDiffers();
    }
}

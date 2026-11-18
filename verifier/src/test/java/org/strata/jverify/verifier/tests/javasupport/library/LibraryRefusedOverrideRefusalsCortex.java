package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call on a library receiver may run the override that the
 * receiver's own class declares. {@code Eq.equals} is refused (it compares
 * {@code float}s), so each method that calls {@code Object.equals} in a
 * program that allocates an {@code Eq} is refused, whether it checks that the
 * call answers true or false, where running {@code Object.equals} would
 * compare the references.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class LibraryRefusedOverrideRefusalsCortex {
    static class Eq {
        @Override
        public boolean equals(Object other) {
            float f = 1.5f;
//                    ^^^^ error: not supported by the direct Cortex translation: floating point
            return f < 2.0f;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static void refusedEquals() {
//              ^^^^^^^^^^^^^ error: the library method 'org.strata.jverify.verifier.tests.javasupport.library.LibraryRefusedOverrideRefusalsCortex.Eq.equals' could not be translated: not supported by the direct Cortex translation: an override that was not translated
        Object a = new Eq();
        check(a.equals(new Eq()));
    }

    static void refusedDiffers() {
//              ^^^^^^^^^^^^^^ error: the library method 'org.strata.jverify.verifier.tests.javasupport.library.LibraryRefusedOverrideRefusalsCortex.Eq.equals' could not be translated: not supported by the direct Cortex translation: an override that was not translated
        Object a = new Eq();
        check(!a.equals(new Eq()));
    }

    @InterpretEntry
    static void run() {
        refusedEquals();
        refusedDiffers();
    }
}

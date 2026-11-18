package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call on a library receiver may run the override of a class whose
 * objects the translation makes itself: here a String from
 * {@code StringBuilder.toString} or from a concatenation, in a program that
 * does not box. {@code String.equals} cannot be translated, so each method
 * that calls {@code Object.equals} is refused, whether it checks that the call
 * answers true or false, where running {@code Object.equals} would compare
 * the references.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class LibraryModelledReceiverRefusalsCortex {
    static void builtEquals() {
//              ^^^^^^^^^^^ error: the library method 'java.lang.String.equals' could not be translated: not supported by the direct Cortex translation: static fields of library classes (java.lang.String.COMPACT_STRINGS)
        StringBuilder sb = new StringBuilder();
        sb.append("a");
        sb.append("b");
        Object o = sb.toString();
        check(o.equals("ab"));
    }

    static void builtDiffers() {
//              ^^^^^^^^^^^^ error: the library method 'java.lang.String.equals' could not be translated: not supported by the direct Cortex translation: static fields of library classes (java.lang.String.COMPACT_STRINGS)
        StringBuilder sb = new StringBuilder();
        sb.append("a");
        sb.append("b");
        Object o = sb.toString();
        check(!o.equals("ab"));
    }

    static void concatenatedEquals() {
//              ^^^^^^^^^^^^^^^^^^ error: the library method 'java.lang.String.equals' could not be translated: not supported by the direct Cortex translation: static fields of library classes (java.lang.String.COMPACT_STRINGS)
        String a = "a";
        Object o = a + "b";
        check(o.equals("ab"));
    }

    static void concatenatedDiffers() {
//              ^^^^^^^^^^^^^^^^^^^ error: the library method 'java.lang.String.equals' could not be translated: not supported by the direct Cortex translation: static fields of library classes (java.lang.String.COMPACT_STRINGS)
        String a = "a";
        Object o = a + "b";
        check(!o.equals("ab"));
    }

    @InterpretEntry
    static void run() {
        builtEquals();
        builtDiffers();
        concatenatedEquals();
        concatenatedDiffers();
    }
}

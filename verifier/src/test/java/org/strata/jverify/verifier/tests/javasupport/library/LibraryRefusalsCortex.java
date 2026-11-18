package org.strata.jverify.verifier.tests.javasupport.library;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The library calls the direct Cortex translation refuses when interpreting,
 * one per method, each with the reason the library method was refused for: a
 * native method; an abstract method; a method that reads a static field of a
 * library class, which no translated initializer assigns; a method refused
 * for a construct in a method it calls; a virtual call whose receiver may be
 * of a class whose override is refused; and a method that calls a refused one
 * only through another program method, which is refused for it in turn.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class LibraryRefusalsCortex {
    static void copied(int[] a, int[] b) {
        System.arraycopy(a, 0, b, 0, 1);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: the library method 'java.lang.System.arraycopy' could not be translated: not supported by the direct Cortex translation: native methods (java.lang.System.arraycopy)
    }

    static int hashed(Object o) {
        return o.hashCode();
//             ^^^^^^^^^^^^ error: the library method 'java.lang.Object.hashCode' could not be translated: not supported by the direct Cortex translation: native methods (java.lang.Object.hashCode)
    }

    static int compared(Comparable<String> a, String b) {
        return a.compareTo(b);
//             ^^^^^^^^^^^^^^ error: the library method 'java.lang.Comparable.compareTo' could not be translated: not supported by the direct Cortex translation: abstract library methods (java.lang.Comparable.compareTo)
    }

    static Object listed() {
        return new java.util.ArrayList<String>();
//             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: the library method 'java.util.ArrayList.<init>' could not be translated: not supported by the direct Cortex translation: static fields of library classes (java.util.ArrayList.DEFAULTCAPACITY_EMPTY_ELEMENTDATA)
    }

    static void ranged(int[] a) {
        java.util.Arrays.fill(a, 0, 1, 7);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: the library method 'java.util.Arrays.fill' could not be translated: the library method 'java.util.Arrays.rangeCheck' could not be translated: not supported by the direct Cortex translation: string conversion of int in a concatenation
    }

    @InterpretEntry
    static void run() {
        check(Math.abs(-1) == 1);
    }
}

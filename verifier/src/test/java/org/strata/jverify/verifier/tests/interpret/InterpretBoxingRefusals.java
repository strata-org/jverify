package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * What the direct Cortex translation refuses around boxes when interpreting,
 * one per method, each with the message that names it: a reference comparison
 * that may be between two boxes, whether the operands are typed as a wrapper,
 * as one of its supertypes or as the merge of a box and another object, in a
 * class that boxes or in one that does not; the wrapper members other than
 * boxing, the unboxing method and {@code equals}; and a read of
 * {@code Boolean.TRUE}, a static field that no translated initializer assigns.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class InterpretBoxingRefusals {
    static class Point {
    }

    static class Holder {
        Integer x;
        Integer y;
    }

    /** A class that does not box: the refusal is for the program, not for the class. */
    static class Elsewhere {
        static boolean objects(Object a, Object b) {
            return a == b;
//                 ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
        }
    }

    static boolean identity(Integer a, Integer b) {
        return a == b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean erased(Object a, Object b) {
        return a != b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean locals() {
        Integer a = 1;
        Object b = a;
        return a == b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean comparable(Comparable<Integer> a, Comparable<Integer> b) {
        return a == b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean number(Number a, Number b) {
        return a == b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean serializable(java.io.Serializable a, java.io.Serializable b) {
        return a == b;
//             ^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean fields(Holder h) {
        return h.x == h.y;
//             ^^^^^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static boolean merged(boolean c, Integer a, Integer b, Point p) {
        return (c ? (Object) a : p) == (c ? (Object) b : p);
//             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: a reference comparison when an operand may be an object that a contract body allocates: its identity may not be Java's
    }

    static int parsed() {
        return Integer.parseInt("1");
//             ^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.lang.Integer.parseInt) when interpreting
    }

    static long widened(Integer a) {
        return a.longValue();
//             ^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.lang.Integer.longValue) when interpreting
    }

    static int hashed(Integer a) {
        return a.hashCode();
//             ^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.lang.Integer.hashCode) when interpreting
    }

    static int compared(Integer a, Integer b) {
        return a.compareTo(b);
//             ^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.lang.Integer.compareTo) when interpreting
    }

    static boolean constant() {
        return Boolean.TRUE;
//             ^^^^^^^^^^^^ error: not supported by the direct Cortex translation: static fields of library classes that hold an object of a class with a contract (java.lang.Boolean.TRUE)
    }

    static void missing() {
        Integer none = null;
//                     ^^^^ error: not supported by the direct Cortex translation: the constant DynamicConstantDesc[ConstantBootstraps::nullConstant()Object]
    }

    @InterpretEntry
    static void run() {
        Integer boxed = 1;
        check(boxed == 1);
    }
}

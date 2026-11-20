package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * What the direct Cortex translation refuses around boxes when interpreting,
 * one per method, each with the message that names it: a reference comparison
 * that may be between two boxes, whether the operands are typed as a wrapper,
 * as one of its supertypes or as the merge of a box and another object, in a
 * class that boxes or in one that does not; and the wrapper members other than
 * boxing, the unboxing method and {@code equals}, which the contract gives no
 * body.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 2)
class BoxingRefusalsCortex {
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
//             ^^^^^^^^^^^^^^^^^^^^^ error: the library method 'java.lang.Integer.parseInt' could not be translated: not supported by the direct Cortex translation: a method of java.lang.Integer that its contract gives no body
    }

    static long widened(Integer a) {
        return a.longValue();
//             ^^^^^^^^^^^^^ error: the library method 'java.lang.Integer.longValue' could not be translated: not supported by the direct Cortex translation: a method of java.lang.Integer that its contract gives no body
    }

    static int hashed(Integer a) {
        return a.hashCode();
//             ^^^^^^^^^^^^ error: the library method 'java.lang.Integer.hashCode' could not be translated: not supported by the direct Cortex translation: a method of java.lang.Integer that its contract gives no body
    }

    static int compared(Integer a, Integer b) {
        return a.compareTo(b);
//             ^^^^^^^^^^^^^^ error: the library method 'java.lang.Integer.compareTo' could not be translated: not supported by the direct Cortex translation: a method of java.lang.Integer that its contract gives no body
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

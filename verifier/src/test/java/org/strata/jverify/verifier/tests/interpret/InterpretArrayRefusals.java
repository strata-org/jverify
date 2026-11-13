package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The array shapes the direct Cortex translation refuses when interpreting,
 * one per method, each with the message that names the shape.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.FRONTEND_ARRAYS},
        exitCode = 2)
class InterpretArrayRefusals {
    static int[] shared;

    static void references() {
        String[] a = new String[1];
//                   ^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: arrays of references
    }

    static void referenceElement(Object[] a) {
//                               ^^^^^^^^^^ error: not supported by the direct Cortex translation: arrays of references
    }

    static void multiDimensional() {
        int[][] a = new int[2][3];
//                  ^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: multi-dimensional arrays
    }

    static void nested() {
        int[][] a = new int[2][];
//                  ^^^^^^^^^^^^ error: not supported by the direct Cortex translation: multi-dimensional arrays
    }

    static void floats() {
        float[] a = new float[1];
//                  ^^^^^^^^^^^^ error: not supported by the direct Cortex translation: floating point
    }

    static void doubles(double[] a) {
//                      ^^^^^^^^^^ error: not supported by the direct Cortex translation: floating point
    }

    static void cloned(int[] a) {
        int[] b = a.clone();
//                ^^^^^^^^^ error: not supported by the direct Cortex translation: the array method clone
    }

    static void copied(int[] a, int[] b) {
        System.arraycopy(a, 0, b, 0, 1);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.lang.System.arraycopy) when interpreting
    }

    static void library(int[] a) {
        java.util.Arrays.fill(a, 1);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ error: not supported by the direct Cortex translation: calls to library methods (java.util.Arrays.fill) when interpreting
    }

    static void staticField() {
        shared = new int[1];
//      ^^^^^^ error: not supported by the direct Cortex translation: arrays in static fields
    }

    static void classLiteral() {
        Object c = int[].class;
//                 ^^^^^^^^^^^ error: not supported by the direct Cortex translation: class literals
    }

    @InterpretEntry
    static void run() {
    }
}

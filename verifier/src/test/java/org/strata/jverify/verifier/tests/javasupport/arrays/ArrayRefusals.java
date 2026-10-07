// Array constructs outside the bytecode frontend's primitive-array model are
// refused per method (static: an error at the method's opening brace), each
// with its own reason, while the primitive-array method ok (next to the
// reference-array one) and the helper one still verify. Refusals that FULL-tier
// source cannot reach (code without -g, a missing or reshaped JArray model,
// writes in library contract code, a non-boolean store into a boolean[]) are
// covered by the bytecode frontend's unit tests.
package org.strata.jverify.verifier.tests.javasupport.arrays;
import java.util.function.IntFunction;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_ARRAYS}, continueOnErrors = true, exitCode = 0, methodsInvalid = 0, errorCount = 0, methodsVerified = 3, methodsSkipped = 20)
class ArrayRefusals {
    static int ok(int[] a) {
        precondition(a.length > 0);
        return a[0];
    }

    static int referenceArray() {
//                              ^ error: arrays of reference types are not yet supported
        return new String[2].length;
    }

    static int one() {
        return 1;
    }

    static int multiDimensional() {
//                                ^ error: multi-dimensional arrays are not yet supported
        return new int[2][2].length;
    }

    static int floatArray() {
//                          ^ error: Unsupported type: float[]
        return new float[2].length;
    }

    static int doubleArray(double[] d) {
//                                     ^ error: Unsupported type: double[]
        return d.length;
    }

    static IntFunction<int[]> constructorReference() {
//                                                   ^ error: array constructor references (int[]::new) are not yet supported
        return int[]::new;
    }

    static void arrayCopy(int[] a, int[] b) {
//                                          ^ error: System.arraycopy is not yet supported
        System.arraycopy(a, 0, b, 0, 1);
    }

    static void arraysFill(int[] a) {
//                                  ^ error: java.util.Arrays methods are not yet supported
        java.util.Arrays.fill(a, 0);
    }

    static int cloneLength(int[] a) {
//                                  ^ error: array clone() is not yet supported
        int[] b = a.clone();
        return b.length;
    }

    static Object asObject(int[] a) {
//                                  ^ error: an array used as an Object is not yet supported
        Object o = a;
        return o;
    }

    static boolean isIntArray(Object o) {
//                                      ^ error: casts and instanceof tests on array types are not yet supported
        return o instanceof int[];
    }

    static int nullableArray(@Nullable int[] a) {
//                                              ^ error: @Nullable arrays are not yet supported
        return 0;
    }

    static void quantifier() {
//                           ^ error: quantifiers over arrays are not yet supported
        check(forall((int[] x) -> x.length >= 0));
    }

    static int enhancedFor(int[] a) {
//                                  ^ error: array access through a local variable with no recorded type (for example an enhanced for loop) is not yet supported
        int s = 0;
        for (int x : a) {
            s = x;
        }
        return s;
    }

    static int conditional(boolean c) {
//                                    ^ error: an array allocation or write inside a condition or conditional expression is not yet supported
        int[] a = c ? new int[1] : new int[2];
        return a.length;
    }

    static int loopConditionRead(int[] a) {
//                                        ^ error: an array element read in a loop condition is not yet supported
        int i = 0;
        while (a[i] != 0) {
            i++;
        }
        return i;
    }

    static int specWrite() {
//                         ^ error: an array allocation or write inside a specification is not yet supported
        postcondition((int r) -> new int[1].length == 1);
        return 0;
    }

    @Pure
    static int pureWrite() {
//                         ^ error: array allocation or writes inside a @Pure method are not yet supported
        return new int[1].length;
    }

    static int order() {
//                     ^ error: an array allocation or write inside an expression with an earlier call or field read is not yet supported
        return one() + new int[1].length;
    }

    static int valueUse(int[] a) {
//                               ^ error: an array element assignment or increment used as a value is not yet supported
        precondition(a.length > 0);
        int x = a[0]++;
        return x;
    }

    static void effectfulCompound(int[] a) {
//                                         ^ error: compound assignment to an array element whose array or index is not a local variable or constant is not yet supported
        precondition(a.length > 1);
        a[one()] += 1;
    }
}

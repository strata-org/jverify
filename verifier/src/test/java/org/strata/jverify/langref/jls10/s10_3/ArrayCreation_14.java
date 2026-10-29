package org.strata.jverify.langref.jls10.s10_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_3.ArrayCreation_14#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayCreation_14 {
    static void run() {

        // Multi-dimensional array creation with only the leading dimension.
        // Trailing components are null until assigned.
        int[][] jagged = new int[2][];
        jagged[0] = new int[] {1, 2};
        jagged[1] = new int[] {3};
        check(jagged[0][1] == 2 && jagged[1][0] == 3);
    }
}

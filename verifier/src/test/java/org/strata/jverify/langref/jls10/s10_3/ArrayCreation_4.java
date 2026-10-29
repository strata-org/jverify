package org.strata.jverify.langref.jls10.s10_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_3.ArrayCreation_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayCreation_4 {
    static void run() {

        // The dimension expression may be any int-valued expression.
        int n = 2 + 2;
        int[] sized = new int[n];
        check(sized.length == 4);
    }
}

package org.strata.jverify.langref.jls15.s15_13;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.13 -- Method Reference Expressions: an array constructor method
// reference (int[]::new) produces an IntFunction that allocates arrays of the
// given size.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_13.MethodRef_ArrayConstructor#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class MethodRef_ArrayConstructor {
    static void run() {

        java.util.function.IntFunction<int[]> f = int[]::new;
        int[] a = f.apply(5);
        check(a.length == 5);
    }
}

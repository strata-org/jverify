package org.strata.jverify.langref.jls15.s15_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.3 -- Array Access Expressions: indexing, read/write, computed indices, multi-dim access
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_10.ArrayAccessExpressions_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayAccessExpressions_3 {
    static void run() {

        int[] a = new int[]{5, 6, 7, 8};

        // read access by computed (runtime) index expression
        int i = 1;
        check(a[i + 1] == 7);
    }
}

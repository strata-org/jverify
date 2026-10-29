package org.strata.jverify.langref.jls15.s15_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_10.ArrayCreationExpressions_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayCreationExpressions_5 {
    static void run() {

        // multidimensional array with all dimensions sized
        int[][] m = new int[2][3];
        check(m.length == 2);
    }
}

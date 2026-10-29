package org.strata.jverify.langref.jls15.s15_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.10.1 -- Array Creation Expressions: new with dim exprs, initializers, multi-dim, partial dims
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_10.ArrayCreationExpressions_14#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayCreationExpressions_14 {
    static void run() {

        // dimension expression computed at runtime
        int n = 1 + 1;
        boolean[] flags = new boolean[n + 2];
        check(!flags[0]);
    }
}

package org.strata.jverify.langref.jls15.s15_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.9 -- Class Instance Creation: diamond (<>) inference for a generic
// class instance creation expression.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_9.NestedGenericDiamond#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NestedGenericDiamond {
    static void run() {

        java.util.List<Integer> l = new java.util.ArrayList<>();
        l.add(3);
        check(l.get(0) == 3);
    }
}

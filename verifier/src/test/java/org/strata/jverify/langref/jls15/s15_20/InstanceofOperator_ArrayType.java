package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- instanceof Operator: an int[] value is an instance of the
// array type int[].
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.InstanceofOperator_ArrayType#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InstanceofOperator_ArrayType {
    static void run() {

        Object o = new int[3];
        check(o instanceof int[]);
    }
}

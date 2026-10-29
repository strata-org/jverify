package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- instanceof Operator: instanceof always yields false for null.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.InstanceofOperator_Null#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InstanceofOperator_Null {
    static void run() {

        Object o = null;
        check(!(o instanceof String));
    }
}

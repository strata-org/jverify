package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.InstanceofOperator_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InstanceofOperator_5 {
    static void run() {

        // null is never an instance of anything
        Object n = null;
        check(!(n instanceof Base_InstanceofOperator_5));
    }
}

interface Named_InstanceofOperator_5 {
    int id();
}

class Base_InstanceofOperator_5 implements Named_InstanceofOperator_5 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_5 extends Base_InstanceofOperator_5 {
}

class OtherBase_InstanceofOperator_5 {
}

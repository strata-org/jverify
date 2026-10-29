package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.InstanceofOperator_6#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class InstanceofOperator_6 {
    static void run() {

        // Pattern instanceof form binds a pattern variable
        Object p = new Sub_InstanceofOperator_6();
        if (p instanceof Base_InstanceofOperator_6 bound) {
            check(bound.id() == 7);
        } else {
        }
    }
}

interface Named_InstanceofOperator_6 {
    int id();
}

class Base_InstanceofOperator_6 implements Named_InstanceofOperator_6 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_6 extends Base_InstanceofOperator_6 {
}

class OtherBase_InstanceofOperator_6 {
}

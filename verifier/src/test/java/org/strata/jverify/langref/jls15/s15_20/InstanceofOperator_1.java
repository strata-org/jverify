package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.InstanceofOperator_1#run")
class InstanceofOperator_1 {
    static void run() {

        Object o = new Sub_InstanceofOperator_1();

        // Relational type-comparison form
        check(o instanceof Sub_InstanceofOperator_1);
    }
}

interface Named_InstanceofOperator_1 {
    int id();
}

class Base_InstanceofOperator_1 implements Named_InstanceofOperator_1 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_1 extends Base_InstanceofOperator_1 {
}

class OtherBase_InstanceofOperator_1 {
}

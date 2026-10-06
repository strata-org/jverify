package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
class InstanceofOperator_3 {
    static void run() {

        Object o = new Sub_InstanceofOperator_3();
        check(o instanceof Named_InstanceofOperator_3);
    }
}

interface Named_InstanceofOperator_3 {
    int id();
}

class Base_InstanceofOperator_3 implements Named_InstanceofOperator_3 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_3 extends Base_InstanceofOperator_3 {
}

class OtherBase_InstanceofOperator_3 {
}

package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
class InstanceofOperator_2 {
    static void run() {

        Object o = new Sub_InstanceofOperator_2();
        check(o instanceof Base_InstanceofOperator_2);
    }
}

interface Named_InstanceofOperator_2 {
    int id();
}

class Base_InstanceofOperator_2 implements Named_InstanceofOperator_2 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_2 extends Base_InstanceofOperator_2 {
}

class OtherBase_InstanceofOperator_2 {
}

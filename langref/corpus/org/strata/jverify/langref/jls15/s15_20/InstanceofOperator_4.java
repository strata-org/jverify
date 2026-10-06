package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
class InstanceofOperator_4 {
    static void run() {

        Object o = new Sub_InstanceofOperator_4();
        check(!(o instanceof OtherBase_InstanceofOperator_4));
    }
}

interface Named_InstanceofOperator_4 {
    int id();
}

class Base_InstanceofOperator_4 implements Named_InstanceofOperator_4 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_4 extends Base_InstanceofOperator_4 {
}

class OtherBase_InstanceofOperator_4 {
}

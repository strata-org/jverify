package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- The instanceof Operator: type-test and pattern instanceof against classes/interfaces
class InstanceofOperator_7 {
    static void run() {

        // Pattern instanceof form binds a pattern variable
        Object p = new Sub_InstanceofOperator_7();
        if (p instanceof Base_InstanceofOperator_7 bound) {
        } else {
            check(false);
        }
    }
}

interface Named_InstanceofOperator_7 {
    int id();
}

class Base_InstanceofOperator_7 implements Named_InstanceofOperator_7 {
    public int id() {
        return 7;
    }
}

class Sub_InstanceofOperator_7 extends Base_InstanceofOperator_7 {
}

class OtherBase_InstanceofOperator_7 {
}

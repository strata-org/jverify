package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- instanceof Operator: instanceof always yields false for null.
class InstanceofOperator_Null {
    static void run() {

        Object o = null;
        check(!(o instanceof String));
    }
}

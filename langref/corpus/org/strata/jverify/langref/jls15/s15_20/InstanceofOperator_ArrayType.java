package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- instanceof Operator: an int[] value is an instance of the
// array type int[].
class InstanceofOperator_ArrayType {
    static void run() {

        Object o = new int[3];
        check(o instanceof int[]);
    }
}

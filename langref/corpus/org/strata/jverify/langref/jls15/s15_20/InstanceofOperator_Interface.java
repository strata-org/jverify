package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.2 -- instanceof Operator: the run-time type check succeeds when the
// class of the object implements the tested interface.
class InstanceofOperator_Interface {
    static void run() {

        Object o = new java.util.ArrayList<Integer>();
        check(o instanceof java.util.List);
    }
}

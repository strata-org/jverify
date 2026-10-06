package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.1 -- Simple Assignment Operator =: an assignment is an expression
// whose value is the value stored in the variable.
class SimpleAssignment_AsExpression {
    static void run() {

        int a;
        check((a = 5) == 5);
    }
}

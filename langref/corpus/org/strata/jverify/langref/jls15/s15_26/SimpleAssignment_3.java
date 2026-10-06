package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.1 -- Simple Assignment Operator =: stores the value of the
// right-hand expression into a variable, and the assignment itself is an
// expression whose value is the value assigned.
class SimpleAssignment_3 {
    static void run() {

        // Assignment is an expression yielding the assigned value.
        int y;
        int z = (y = 7);
        check(z == 7);
    }
}

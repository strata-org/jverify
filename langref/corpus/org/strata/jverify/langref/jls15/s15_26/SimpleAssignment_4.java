package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.1 -- Simple Assignment Operator =: stores the value of the
// right-hand expression into a variable, and the assignment itself is an
// expression whose value is the value assigned.
class SimpleAssignment_4 {
    static void run() {

        // Right-hand side is evaluated before the store; chained assignment
        // associates right-to-left.
        int a;
        int b;
        a = b = 9;
        check(a == 9);
    }
}

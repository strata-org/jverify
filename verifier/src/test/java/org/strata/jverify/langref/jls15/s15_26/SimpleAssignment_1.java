package org.strata.jverify.langref.jls15.s15_26;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.1 -- Simple Assignment Operator =: stores the value of the
// right-hand expression into a variable, and the assignment itself is an
// expression whose value is the value assigned.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_26.SimpleAssignment_1#run")
class SimpleAssignment_1 {
    static void run() {

        int x;
        x = 5;
        check(x == 5);
    }
}

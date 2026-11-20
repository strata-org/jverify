package org.strata.jverify.langref.jls15.s15_26;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.1 -- Simple Assignment Operator =: stores the value of the
// right-hand expression into a variable, and the assignment itself is an
// expression whose value is the value assigned.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_26.SimpleAssignment_6#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class SimpleAssignment_6 {
    static void run() {

        // Reference variable assignment.
        int[] arr = new int[3];
        arr[1] = 42;
        check(arr[1] == 42);
    }
}

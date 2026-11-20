package org.strata.jverify.langref.jls5.s5_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.2 -- Assignment Contexts: conversions allowed when assigning an expression to a variable.
// Exercises identity, widening primitive, narrowing of constant expressions, widening reference,
// boxing, and unboxing conversions in assignment position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_2.AssignmentContexts_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class AssignmentContexts_4 {
    static void run() {

        // Identity conversion
        int a = 5;

        // Widening primitive conversion: int -> long -> float -> double
        long l = a;
        float f = l;           // long -> float
        check(f == 5.0f);
    }
}

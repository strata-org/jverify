package org.strata.jverify.langref.jls5.s5_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.2 -- Assignment Contexts: conversions allowed when assigning an expression to a variable.
// Exercises identity, widening primitive, narrowing of constant expressions, widening reference,
// boxing, and unboxing conversions in assignment position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_2.AssignmentContexts_7#run")
class AssignmentContexts_7 {
    static void run() {

        // Narrowing of a constant expression: int constant in byte range may be assigned to byte
        byte nb = 42;          // 42 fits in byte
        check(nb == 42);
    }
}

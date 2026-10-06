package org.strata.jverify.langref.jls5.s5_2;

import static org.strata.jverify.JVerify.*;

// JLS 5.2 -- Assignment Contexts: conversions allowed when assigning an expression to a variable.
// Exercises identity, widening primitive, narrowing of constant expressions, widening reference,
// boxing, and unboxing conversions in assignment position.
class AssignmentContexts_14 {
    static void run() {

        // null may be assigned to any reference type
        Object on = null;
        check(on == null);
    }
}

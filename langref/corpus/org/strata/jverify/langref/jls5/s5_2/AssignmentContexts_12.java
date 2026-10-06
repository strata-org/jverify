package org.strata.jverify.langref.jls5.s5_2;

import static org.strata.jverify.JVerify.*;

// JLS 5.2 -- Assignment Contexts: conversions allowed when assigning an expression to a variable.
// Exercises identity, widening primitive, narrowing of constant expressions, widening reference,
// boxing, and unboxing conversions in assignment position.
class AssignmentContexts_12 {
    static void run() {

        // Unboxing conversion: Integer -> int
        Integer src = 7;
        int unboxed = src;     // Integer -> int
        check(unboxed == 7);
    }
}

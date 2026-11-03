package org.strata.jverify.langref.jls5.s5_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.2 -- Assignment Contexts: conversions allowed when assigning an expression to a variable.
// Exercises identity, widening primitive, narrowing of constant expressions, widening reference,
// boxing, and unboxing conversions in assignment position.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_2.AssignmentContexts_13#run")
class AssignmentContexts_13 {
    static void run() {

        // Unboxing conversion: Integer -> int
        Integer src = 7;

        // Boxing then widening: int -> Integer (boxing), but Integer -> long needs unbox+widen
        long fromBox = src;    // Integer -> int (unbox) -> long (widen)
        check(fromBox == 7L);
    }
}

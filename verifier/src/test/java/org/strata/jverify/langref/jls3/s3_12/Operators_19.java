package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_19#run")
class Operators_19 {
    static void run() {

        // logical '&&', '||', '!'
        boolean t = true;
        boolean f = false;
        check(t || f);
    }
}

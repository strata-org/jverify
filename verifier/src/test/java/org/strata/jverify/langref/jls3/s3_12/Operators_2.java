package org.strata.jverify.langref.jls3.s3_12;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_12.Operators_2#run")
class Operators_2 {
    static void run() {

        // assignment '=' and arithmetic '+', '-', '*', '/', '%'
        int a = 7;
        int b = 3;
        check(a - b == 4);
    }
}

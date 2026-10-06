package org.strata.jverify.langref.jls3.s3_12;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
class Operators_12 {
    static void run() {

        // assignment '=' and arithmetic '+', '-', '*', '/', '%'
        int a = 7;
        int b = 3;

        // relational '<', '>', '<=', '>='
        check(b < a);
    }
}

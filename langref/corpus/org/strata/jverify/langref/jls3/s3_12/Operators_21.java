package org.strata.jverify.langref.jls3.s3_12;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
class Operators_21 {
    static void run() {

        // bitwise '&', '|', '^', '~' (and boolean &, |, ^)
        check((0b1100 & 0b1010) == 0b1000);
    }
}

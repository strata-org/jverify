package org.strata.jverify.langref.jls3.s3_12;

import static org.strata.jverify.JVerify.*;

// JLS 3.12 -- Operators: exercises the operator tokens enumerated in JLS 3.12
// (arithmetic, relational, equality, logical, bitwise, shift, assignment,
// conditional, increment/decrement, unary).
class Operators_8 {
    static void run() {

        // increment '++' and decrement '--'
        int c = 5;
        c++;
        check(c == 6);
    }
}

package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: when one operand is a String,
// + performs string conversion of the other operand and concatenates.
class StringConcatenationOperator_8 {
    static void run() {

        // left-associative chaining
        String chain = 1 + 2 + "=" + 3; // (1+2) numeric -> "3", then "3=" then "3=3"
        check(chain.equals("3=3"));
    }
}

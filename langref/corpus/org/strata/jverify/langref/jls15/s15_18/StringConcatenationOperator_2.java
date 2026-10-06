package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: when one operand is a String,
// + performs string conversion of the other operand and concatenates.
class StringConcatenationOperator_2 {
    static void run() {

        String a = "ab";
        String b = "cd";
        String s = a + b;            // String + String

        check(s.charAt(0) == 'a');
    }
}

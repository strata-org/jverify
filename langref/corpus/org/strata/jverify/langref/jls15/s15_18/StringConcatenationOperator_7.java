package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: when one operand is a String,
// + performs string conversion of the other operand and concatenates.
class StringConcatenationOperator_7 {
    static void run() {

        String withBool = "v=" + true; // String + boolean
        check(withBool.equals("v=true"));
    }
}

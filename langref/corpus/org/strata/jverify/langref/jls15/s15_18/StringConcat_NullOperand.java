package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: a null reference operand is
// converted to the string "null" prior to concatenation.
class StringConcat_NullOperand {
    static void run() {

        String s = "x" + null;
        check(s.equals("xnull"));
    }
}

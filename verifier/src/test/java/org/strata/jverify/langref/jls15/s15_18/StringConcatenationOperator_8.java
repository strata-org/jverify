package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: when one operand is a String,
// + performs string conversion of the other operand and concatenates.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.StringConcatenationOperator_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringConcatenationOperator_8 {
    static void run() {

        // left-associative chaining
        String chain = 1 + 2 + "=" + 3; // (1+2) numeric -> "3", then "3=" then "3=3"
        check(chain.equals("3=3"));
    }
}

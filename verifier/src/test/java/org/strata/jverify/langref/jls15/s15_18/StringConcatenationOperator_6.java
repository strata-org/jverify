package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: when one operand is a String,
// + performs string conversion of the other operand and concatenates.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.StringConcatenationOperator_6#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringConcatenationOperator_6 {
    static void run() {

        String withChar = "n" + 'o'; // String + char
        check(withChar.equals("no"));
    }
}

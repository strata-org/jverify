package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: a non-string operand is
// converted using its toString() before concatenation.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.StringConcat_ObjectToString#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringConcat_ObjectToString {
    static void run() {

        Object o = new Object() { public String toString() { return "obj"; } };
        String s = "x" + o;
        check(s.equals("xobj"));
    }
}

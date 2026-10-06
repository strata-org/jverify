package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.1 -- String Concatenation Operator +: a non-string operand is
// converted using its toString() before concatenation.
class StringConcat_ObjectToString {
    static void run() {

        Object o = new Object() { public String toString() { return "obj"; } };
        String s = "x" + o;
        check(s.equals("xobj"));
    }
}

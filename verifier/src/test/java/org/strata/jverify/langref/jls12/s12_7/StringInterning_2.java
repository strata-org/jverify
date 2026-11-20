package org.strata.jverify.langref.jls12.s12_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.28 / 3.10.5 -- Compile-time constant expressions of type String are
// computed at compile time; the concatenation "ab" + "cd" is a constant
// expression, produces the interned "abcd", and is `==` to the literal
// "abcd".
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_7.StringInterning_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ERROR})
class StringInterning_2 {
    static void run() {
        String a = "ab" + "cd";
        String b = "abcd";
        check(a == b);
    }
}

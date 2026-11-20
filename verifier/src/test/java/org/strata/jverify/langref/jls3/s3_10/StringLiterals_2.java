package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: double-quoted strings, escapes, concatenation, char access
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.StringLiterals_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringLiterals_2 {
    static void run() {

        String s = "hello";

        check(s.charAt(0) == 'h');
    }
}

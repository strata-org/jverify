package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: double-quoted strings, escapes, concatenation, char access
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.StringLiterals_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringLiterals_4 {
    static void run() {

        String escaped = "a\tb\"c";

        check(escaped.charAt(3) == '"');
    }
}

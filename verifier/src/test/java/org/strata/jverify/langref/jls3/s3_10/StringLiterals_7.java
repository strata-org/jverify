package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: identical string literals refer to the same interned String instance.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.StringLiterals_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ERROR})
class StringLiterals_7 {
    static void run() {

        String a = "abc";
        String b = "abc";
        check(a == b);
    }
}

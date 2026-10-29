package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.5 -- String Literals: Unicode escape inside a string literal is processed
// before lexical analysis; the escape resolves to U+00E9 (LATIN SMALL LETTER E WITH ACUTE).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.StringLiterals_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class StringLiterals_8 {
    static void run() {

        String s = "caf\u00E9";
        check(s.length() == 4);
    }
}

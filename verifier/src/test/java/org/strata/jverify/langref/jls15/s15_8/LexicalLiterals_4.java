package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.1 -- Lexical Literals: integer, floating-point, boolean, char, and null literals as primary expressions
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.LexicalLiterals_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LexicalLiterals_4 {
    static void run() {

        float f = 2.0f;
        check(f == 2.0f);
    }
}

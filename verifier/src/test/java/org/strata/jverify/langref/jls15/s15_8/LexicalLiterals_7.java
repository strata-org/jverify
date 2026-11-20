package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.1 -- Lexical Literals: integer, floating-point, boolean, char, and null literals as primary expressions
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.LexicalLiterals_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LexicalLiterals_7 {
    static void run() {

        Object o = null;
        check(o == null);
    }
}

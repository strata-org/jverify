package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.1 -- Lexical Literals: integer, floating-point, boolean, char, and null literals as primary expressions
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.LexicalLiterals_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LexicalLiterals_3 {
    static void run() {

        double d = 3.5;
        check(d == 3.5);
    }
}

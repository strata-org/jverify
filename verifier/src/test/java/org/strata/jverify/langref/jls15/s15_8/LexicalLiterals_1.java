package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.1 -- Lexical Literals: integer, floating-point, boolean, char, and null literals as primary expressions
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.LexicalLiterals_1#run")
class LexicalLiterals_1 {
    static void run() {

        int i = 42;
        check(i == 42);
    }
}

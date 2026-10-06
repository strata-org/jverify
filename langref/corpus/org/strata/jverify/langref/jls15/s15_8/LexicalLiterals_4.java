package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.1 -- Lexical Literals: integer, floating-point, boolean, char, and null literals as primary expressions
class LexicalLiterals_4 {
    static void run() {

        float f = 2.0f;
        check(f == 2.0f);
    }
}

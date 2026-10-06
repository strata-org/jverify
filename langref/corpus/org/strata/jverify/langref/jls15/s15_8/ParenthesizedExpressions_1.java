package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.5 -- Parenthesized Expressions: parentheses group subexpressions and override default precedence
class ParenthesizedExpressions_1 {
    static void run() {

        int noParen = 2 + 3 * 4;
        check(noParen == 14);
    }
}

package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.5 -- Parenthesized Expressions: parentheses group subexpressions and override default precedence
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.ParenthesizedExpressions_1#run")
class ParenthesizedExpressions_1 {
    static void run() {

        int noParen = 2 + 3 * 4;
        check(noParen == 14);
    }
}

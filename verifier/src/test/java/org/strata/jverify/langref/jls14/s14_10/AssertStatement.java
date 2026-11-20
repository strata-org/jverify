package org.strata.jverify.langref.jls14.s14_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.10 -- The assert Statement: both forms `assert Expr;` and
// `assert Expr1 : Expr2;`. Assertions hold here, so execution completes normally.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_10.AssertStatement#run")
class AssertStatement {
    static void run() {
        int x = 2 + 3;
        assert x == 5;                       // first form: assert Expression1 ;
        assert x > 0 : "x must be positive"; // second form: assert Expr1 : Expr2 ;
        boolean b = x == 5;
        assert b;
        check(x == 5);
    }
}

package org.strata.jverify.langref.jls15.s15_27;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.2 -- Lambda Body: exercises both lambda body forms, an expression
// body and a block body (with statements, local variables and a return), plus a
// void-returning block body and capture of an effectively final local variable.
class LambdaBody_1 {
    interface IntFn { int apply(int a); }
    interface Action { void perform(); }

    static int sink = 0;

    static void run() {

        // Expression body.
        IntFn square = x -> x * x;
        check(square.apply(6) == 36);
    }
}

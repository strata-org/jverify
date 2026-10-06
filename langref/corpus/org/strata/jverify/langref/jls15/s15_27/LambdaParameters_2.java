package org.strata.jverify.langref.jls15.s15_27;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.1 -- Lambda Parameters: exercises the several lambda parameter forms
// (zero params, single inferred param without parens, multiple inferred params,
// explicitly typed params, and a var-declared param).
class LambdaParameters_2 {
    interface Supplier { int get(); }
    interface UnaryOp { int apply(int a); }
    interface BinaryOp { int apply(int a, int b); }

    static void run() {

        // Single inferred parameter without parentheses.
        UnaryOp inc = x -> x + 1;
        check(inc.apply(10) == 11);
    }
}

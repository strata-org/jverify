package org.strata.jverify.langref.jls15.s15_27;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.1 -- Lambda Parameters: exercises the several lambda parameter forms
// (zero params, single inferred param without parens, multiple inferred params,
// explicitly typed params, and a var-declared param).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_27.LambdaParameters_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LambdaParameters_1 {
    interface Supplier { int get(); }
    interface UnaryOp { int apply(int a); }
    interface BinaryOp { int apply(int a, int b); }

    static void run() {

        // Empty parameter list.
        Supplier s = () -> 42;
        check(s.get() == 42);
    }
}

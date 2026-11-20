package org.strata.jverify.langref.jls6.s6_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.3 -- Scope with a nested block declaring a local variable that
// shadows the outer local of the same name. The inner assignment binds to
// the inner `x`; after the block ends the outer `x` is unaffected.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_3.Scope_2#run")
class Scope_2 {
    static void run() {
        int x = 1;
        int r;
        {
            int x2 = 2; // inner block; shadowing demonstrated by Scope semantics
            r = x2;
        }
        check(r == 2);
    }
}

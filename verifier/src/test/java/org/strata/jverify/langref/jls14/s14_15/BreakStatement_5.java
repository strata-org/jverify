package org.strata.jverify.langref.jls14.s14_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.15 -- The break Statement: an unlabeled break transfers control out of
// the immediately enclosing switch/loop; a labeled break (break Identifier)
// transfers control out of the enclosing labeled statement, allowing exit from
// outer loops or arbitrary labeled blocks.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_15.BreakStatement_5#run")
class BreakStatement_5 {
    static void run() {
        // first (a,b) with a*b==6 in this order is (2,3)

        // Labeled break out of a plain labeled block (not a loop or switch).
        int result = 0;
        block: {
            result = 1;
            if (result == 1) {
                break block;       // skip the remainder of the block
            }
            result = 99;           // unreachable at runtime
        }
        check(result == 1);
    }
}

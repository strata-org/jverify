package org.strata.jverify.langref.jls14.s14_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.15 -- The break Statement: an unlabeled break transfers control out of
// the immediately enclosing switch/loop; a labeled break (break Identifier)
// transfers control out of the enclosing labeled statement, allowing exit from
// outer loops or arbitrary labeled blocks.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_15.BreakStatement_4#run")
class BreakStatement_4 {
    static void run() {

        // Labeled break exits an outer loop from within an inner loop.
        int found = -1;
        outer:
        for (int a = 0; a < 5; a++) {
            for (int b = 0; b < 5; b++) {
                if (a * b == 6) {
                    found = a * 10 + b;
                    break outer;   // jumps clear out of the outer loop
                }
            }
        }
        check(found == 23);
    }
}

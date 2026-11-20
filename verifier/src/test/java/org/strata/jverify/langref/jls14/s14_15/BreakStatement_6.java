package org.strata.jverify.langref.jls14.s14_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.15 -- The break Statement: an unlabeled break transfers control out of
// the immediately enclosing switch/loop; a labeled break (break Identifier)
// transfers control out of the enclosing labeled statement, allowing exit from
// outer loops or arbitrary labeled blocks.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_15.BreakStatement_6#run")
class BreakStatement_6 {
    static void run() {

        // Labeled break selecting which loop level to leave.
        int iterations = 0;
        loopA:
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                iterations++;
                if (b == 1) {
                    break;         // break only the inner loop
                }
            }
            if (a == 1) {
                break loopA;       // break the outer loop entirely
            }
        }
        // a=0: b=0,1 (2 iters); a=1: b=0,1 (2 iters) then break loopA
        check(iterations == 4);
    }
}

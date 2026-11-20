package org.strata.jverify.langref.jls14.s14_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.15 -- The break Statement: an unlabeled break transfers control out of
// the immediately enclosing switch/loop; a labeled break (break Identifier)
// transfers control out of the enclosing labeled statement, allowing exit from
// outer loops or arbitrary labeled blocks.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_15.BreakStatement_3#run")
class BreakStatement_3 {
    static void run() {

        // Unlabeled break inside a switch exits the switch, not the loop.
        int switchCount = 0;
        int after = 0;
        for (int i = 0; i < 3; i++) {
            switch (i) {
                case 1:
                    switchCount++;
                    break;      // leaves the switch only
                default:
                    switchCount += 10;
            }
            after++;            // still reached for every iteration
        }  // i=0 default, i=1 case, i=2 default
        check(after == 3);
    }
}

package org.strata.jverify.langref.jls14.s14_7;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.7 -- Labeled Statements: break with a label exits the labeled (outer) loop.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_7.LabeledBreak#run")
class LabeledBreak {
    static void run() {
        int count = 0;
        outer:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                count++;
                if (i == 1 && j == 1) {
                    break outer;
                }
            }
        }
        // First full pass of inner loop: 3 iterations (i=0).
        // Second pass: i=1, j=0 (count++), j=1 (count++ then break) -> 2 more.
        check(count == 5);
    }
}

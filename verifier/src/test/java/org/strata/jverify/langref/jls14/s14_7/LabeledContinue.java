package org.strata.jverify.langref.jls14.s14_7;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.7 -- Labeled Statements: continue with a label resumes the labeled (outer) loop.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_7.LabeledContinue#run")
class LabeledContinue {
    static void run() {
        int count = 0;
        outer:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (j == 1) {
                    continue outer;
                }
                count++;
            }
        }
        // For each i (0,1,2): j=0 counts (count++), then j=1 continues outer.
        // So count increments once per outer iteration -> 3.
        check(count == 3);
    }
}

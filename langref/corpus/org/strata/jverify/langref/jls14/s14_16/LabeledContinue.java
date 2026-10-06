package org.strata.jverify.langref.jls14.s14_16;

import static org.strata.jverify.JVerify.*;

// JLS 14.16 -- The continue Statement: labeled continue in nested loops.
// "continue outer" continues the labeled enclosing loop, skipping the
// rest of the inner loop iterations for the current outer iteration.
class LabeledContinue {
    static void run() {
        int hits = 0;
        outer:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (j == 1) {
                    continue outer;   // abandon inner loop, advance i
                }
                hits++;               // only j==0 reached each outer pass -> 3
            }
        }
        check(hits == 3);
    }
}

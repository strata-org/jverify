package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- Compound Assignment Operators: the left-hand-side expression
// is evaluated exactly once, so any side effects in indexing or method calls
// on the LHS happen a single time.
class CompoundAssignment_LHSOnce {
    static int calls = 0;
    static int idx() { calls++; return 0; }

    static void run() {

        calls = 0;
        int[] a = {10};
        a[idx()] += 5;
        check(calls == 1 && a[0] == 15);
    }
}

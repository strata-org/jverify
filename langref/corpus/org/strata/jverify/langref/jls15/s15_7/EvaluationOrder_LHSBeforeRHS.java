package org.strata.jverify.langref.jls15.s15_7;

import static org.strata.jverify.JVerify.*;

// JLS 15.7 / 15.26 -- in an assignment, the left-hand side (including any
// index expression) is fully evaluated before the right-hand side.
class EvaluationOrder_LHSBeforeRHS {
    static java.util.List<Integer> log = new java.util.ArrayList<>();

    static int idx() {
        log.add(0);
        return 0;
    }

    static int val() {
        log.add(1);
        return 5;
    }

    static void run() {
        log.clear();
        int[] a = new int[1];
        a[idx()] = val();
        check(log.equals(java.util.List.of(0, 1)) && a[0] == 5);
    }
}

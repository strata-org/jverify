package org.strata.jverify.examples;

import static org.strata.jverify.JVerify.*;

class GaussianSum {
    /**
     * Returns 0 + 1 + 2 + ... + n.
     *
     * JVerify proves that the result equals n * (n + 1) / 2 for every n
     * from 0 to 46340 (a larger n overflows int). The proof relies on two
     * loop invariants: the range of the loop counter, and the formula for s
     * that holds each time the loop condition is tested.
     */
    static int sumTo(int n) {
        precondition(0 <= n && n <= 46340);
        postcondition((int res) -> res == n * (n + 1) / 2);
        int i = 0;
        int s = 0;
        while (i < n) {
            invariant(0 <= i && i <= n);
            invariant(s == i * (i + 1) / 2);
            i = i + 1;
            s = s + i;
        }
        return s;
    }
}

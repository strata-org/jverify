package org.strata.jverify.examples;

import static org.strata.jverify.JVerify.*;

class GaussianSum {
    /**
     * Returns 0 + 1 + 2 + ... + n.
     *
     * JVerify proves that the result equals the closed form n * (n + 1) / 2
     * for every n in [0, 65535]. The proof relies on two loop invariants:
     * a bound on the loop counter, and a closed-form relation that holds at
     * every iteration.
     */
    static int sumTo(int n) {
        precondition(0 <= n && n <= 65535);
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

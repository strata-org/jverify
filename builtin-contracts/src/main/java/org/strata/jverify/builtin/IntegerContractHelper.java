package org.strata.jverify.builtin;

import org.strata.jverify.Pure;

class IntegerContractHelper {
    @Pure
    public static boolean sumWillOverflow(int a, int b) {
        if (a > 0 && b > 0) {
            return a > Integer.MAX_VALUE - b;
        }
        if (a < 0 && b < 0) {
            return a < Integer.MIN_VALUE - b;
        }
        return false; // Different signs won't overflow
    }
}
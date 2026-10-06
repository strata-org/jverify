package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NegativeArraySizeException when an array is created with a
// negative size.
class NegativeArraySizeException_1 {
    static void run() {
        int n = -1;
        boolean caught = false;
        try {
            int[] a = new int[n];
        } catch (NegativeArraySizeException e) {
            caught = true;
        }
        check(caught);
    }
}

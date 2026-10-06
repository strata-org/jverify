package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArrayIndexOutOfBoundsException on positive out-of-range index.
class ArrayIndexOutOfBoundsException_Positive {
    static void run() {
        int[] a = new int[3];
        boolean caught = false;
        try {
            int x = a[3];
        } catch (ArrayIndexOutOfBoundsException e) {
            caught = true;
        }
        check(caught);
    }
}

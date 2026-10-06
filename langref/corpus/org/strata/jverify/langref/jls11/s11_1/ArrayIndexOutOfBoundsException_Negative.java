package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArrayIndexOutOfBoundsException on negative index.
class ArrayIndexOutOfBoundsException_Negative {
    static void run() {
        int[] a = new int[3];
        boolean caught = false;
        try {
            int x = a[-1];
        } catch (ArrayIndexOutOfBoundsException e) {
            caught = true;
        }
        check(caught);
    }
}

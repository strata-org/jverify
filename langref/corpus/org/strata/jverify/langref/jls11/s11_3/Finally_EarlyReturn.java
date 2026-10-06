package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- finally runs even when the try body returns; the return
// value is preserved (no finally-return here) but the side-effect executes.
class Finally_EarlyReturn {
    static int[] side = new int[1];

    static int m() {
        try {
            return 1;
        } finally {
            side[0] = 99;
        }
    }

    static void run() {
        side[0] = 0;
        check(m() == 1 && side[0] == 99);
    }
}

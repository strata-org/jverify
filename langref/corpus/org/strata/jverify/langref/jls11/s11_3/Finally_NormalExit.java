package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- finally runs on normal completion of the try block.
class Finally_NormalExit {
    static void run() {
        int[] side = new int[1];
        try {
            side[0] = 1;
        } finally {
            side[0] += 10;
        }
        check(side[0] == 11);
    }
}

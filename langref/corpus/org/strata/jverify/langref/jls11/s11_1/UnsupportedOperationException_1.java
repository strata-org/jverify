package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- UnsupportedOperationException from mutating an immutable
// collection returned by List.of.
class UnsupportedOperationException_1 {
    static void run() {
        boolean caught = false;
        try {
            java.util.List.of(1).add(2);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        check(caught);
    }
}

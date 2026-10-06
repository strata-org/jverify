package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.3 -- Run-Time Handling: if no catch clause in the current method
// matches, the method completes abruptly and the exception propagates up the
// invocation chain to the nearest enclosing handler.
class Propagation {
    static void run() {
        int caught = 0;
        try {
            level1();
        } catch (RuntimeException e) {
            caught = 1;
        }
        check(caught == 1);
    }

    static void level1() {
        level2();
    }

    static void level2() {
        throw new IllegalArgumentException("deep");
    }
}

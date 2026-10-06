package org.strata.jverify.langref.jls15.s15_7;

import static org.strata.jverify.JVerify.*;

// JLS 15.7.3 -- if the left-hand operand completes abruptly (e.g. throws),
// no part of the right-hand operand is evaluated.
class EvaluationOrder_AbruptCompletion {
    static int calls = 0;

    static int explode() {
        throw new RuntimeException("boom");
    }

    static int increment() {
        calls++;
        return 0;
    }

    static void run() {
        calls = 0;
        boolean caught = false;
        try {
            int r = explode() + increment();
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught && calls == 0);
    }
}

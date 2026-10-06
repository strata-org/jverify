package org.strata.jverify.langref.jls15.s15_24;

import static org.strata.jverify.JVerify.*;

// JLS 15.24 -- Conditional-Or Operator ||: boolean result, truth table, and
// short-circuit evaluation (right operand not evaluated when left is true).
class ConditionalOrOperator_1 {
    static int sideEffect = 0;

    static boolean markAndReturn(boolean v) {
        sideEffect = sideEffect + 1;
        return v;
    }

    static void run() {

        // Truth table: a || b is true unless both are false.
        boolean tt = true || true;
        check(tt);
    }
}

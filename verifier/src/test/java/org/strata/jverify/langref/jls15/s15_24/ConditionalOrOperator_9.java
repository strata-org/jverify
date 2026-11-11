package org.strata.jverify.langref.jls15.s15_24;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.24 -- Conditional-Or Operator ||: boolean result, truth table, and
// short-circuit evaluation (right operand not evaluated when left is true).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_24.ConditionalOrOperator_9#run")
class ConditionalOrOperator_9 {
    static int sideEffect = 0;

    static boolean markAndReturn(boolean v) {
        sideEffect = sideEffect + 1;
        return v;
    }

    static void run() {

        // Short-circuit: when the left operand is true, the right is NOT evaluated.
        sideEffect = 0;
        boolean s = true || markAndReturn(true);

        // When the left operand is false, the right operand IS evaluated.
        sideEffect = 0;
        boolean t = false || markAndReturn(true);
        check(sideEffect == 1);
    }
}

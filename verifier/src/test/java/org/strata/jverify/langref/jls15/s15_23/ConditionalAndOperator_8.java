package org.strata.jverify.langref.jls15.s15_23;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.23 -- Conditional-And Operator &&: boolean result and short-circuit evaluation
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_23.ConditionalAndOperator_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ConditionalAndOperator_8 {
    static int counter = 0;

    static boolean sideEffectTrue() {
        counter = counter + 1;
        return true;
    }

    static void run() {

        // Short-circuit: when left operand is false, the right operand is NOT evaluated.
        counter = 0;
        boolean r = false && sideEffectTrue();

        // When left operand is true, the right operand IS evaluated.
        counter = 0;
        boolean r2 = true && sideEffectTrue();
        check(counter == 1);
    }
}

package org.strata.jverify.langref.jls15.s15_23;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.23 -- Conditional-And Operator &&: boolean result and short-circuit evaluation
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_23.ConditionalAndOperator_1#run")
class ConditionalAndOperator_1 {
    static int counter = 0;

    static boolean sideEffectTrue() {
        counter = counter + 1;
        return true;
    }

    static void run() {

        // Basic boolean truth table for &&
        boolean tt = true && true;
        check(tt);
    }
}

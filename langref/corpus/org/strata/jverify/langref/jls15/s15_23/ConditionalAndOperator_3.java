package org.strata.jverify.langref.jls15.s15_23;

import static org.strata.jverify.JVerify.*;

// JLS 15.23 -- Conditional-And Operator &&: boolean result and short-circuit evaluation
class ConditionalAndOperator_3 {
    static int counter = 0;

    static boolean sideEffectTrue() {
        counter = counter + 1;
        return true;
    }

    static void run() {

        boolean ft = false && true;
        check(!ft);
    }
}

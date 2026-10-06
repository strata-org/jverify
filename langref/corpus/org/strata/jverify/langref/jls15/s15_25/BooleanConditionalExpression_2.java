package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.1 -- Boolean Conditional Expressions: ? : where both operands are boolean (or Boolean),
// yielding a boolean result, with only the selected branch evaluated.
class BooleanConditionalExpression_2 {
    static int counter = 0;

    static boolean sideEffect(boolean ret) {
        counter = counter + 1;
        return ret;
    }

    static void run() {

        boolean b = false ? true : false;
        check(!b);
    }
}

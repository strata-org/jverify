package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment Operator ++: the value of the expression is
// the value of the variable BEFORE the new (incremented) value is stored.
class PostfixIncrementOperator_1 {
    static void run() {

        int x = 3;
        int y = x++;   // y gets old value (3), x becomes 4
        check(y == 3);
    }
}

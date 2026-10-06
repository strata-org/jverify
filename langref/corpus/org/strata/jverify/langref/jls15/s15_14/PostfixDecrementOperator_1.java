package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.3 -- Postfix Decrement Operator --: the value of the expression is
// the value of the variable BEFORE the new (decremented) value is stored.
class PostfixDecrementOperator_1 {
    static void run() {

        int x = 3;
        int y = x--;   // y gets old value (3), x becomes 2
        check(y == 3);
    }
}

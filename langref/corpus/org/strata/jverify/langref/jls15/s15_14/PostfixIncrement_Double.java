package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a double: adds 1.0 to the
// variable and yields the pre-increment value.
class PostfixIncrement_Double {
    static void run() {

        double x = 1.5;
        double y = x++;
        check(x == 2.5 && y == 1.5);
    }
}

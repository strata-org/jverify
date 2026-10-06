package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a float: adds 1.0f; yields the
// pre-increment float value.
class PostfixIncrement_Float {
    static void run() {

        float x = 1.0f;
        float y = x++;
        check(x == 2.0f && y == 1.0f);
    }
}

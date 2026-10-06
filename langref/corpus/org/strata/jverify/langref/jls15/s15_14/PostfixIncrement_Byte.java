package org.strata.jverify.langref.jls15.s15_14;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a byte: unary numeric promotion
// applies for the addition, then a narrowing conversion back to byte stores the
// new value; the expression yields the pre-increment byte value.
class PostfixIncrement_Byte {
    static void run() {

        byte x = 5;
        byte y = x++;
        check(x == 6 && y == 5);
    }
}

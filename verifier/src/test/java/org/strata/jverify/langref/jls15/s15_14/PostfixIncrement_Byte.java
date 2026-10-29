package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a byte: unary numeric promotion
// applies for the addition, then a narrowing conversion back to byte stores the
// new value; the expression yields the pre-increment byte value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixIncrement_Byte#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class PostfixIncrement_Byte {
    static void run() {

        byte x = 5;
        byte y = x++;
        check(x == 6 && y == 5);
    }
}

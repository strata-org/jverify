package org.strata.jverify.langref.jls15.s15_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.14.2 -- Postfix Increment ++ applied to a char: advances the char by
// one code point; yields the pre-increment char value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_14.PostfixIncrement_Char#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class PostfixIncrement_Char {
    static void run() {

        char x = 'a';
        char y = x++;
        check(x == 'b' && y == 'a');
    }
}

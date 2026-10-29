package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment ++ applied to a char: the expression yields
// the post-increment char value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixIncrement_Char#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class PrefixIncrement_Char {
    static void run() {

        char x = 'a';
        char y = ++x;
        check(x == 'b' && y == 'b');
    }
}

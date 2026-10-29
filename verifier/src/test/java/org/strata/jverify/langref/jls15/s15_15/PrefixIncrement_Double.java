package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment ++ applied to a double: adds 1.0 and yields
// the post-increment value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixIncrement_Double#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class PrefixIncrement_Double {
    static void run() {

        double x = 1.5;
        double y = ++x;
        check(x == 2.5 && y == 2.5);
    }
}

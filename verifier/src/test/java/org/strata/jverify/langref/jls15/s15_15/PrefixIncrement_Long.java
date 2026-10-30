package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment ++ applied to a long: the expression yields
// the post-increment value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixIncrement_Long#run")
class PrefixIncrement_Long {
    static void run() {

        long x = 5;
        long y = ++x;
        check(x == 6 && y == 6);
    }
}

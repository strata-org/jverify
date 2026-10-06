package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment ++ applied to a long: the expression yields
// the post-increment value.
class PrefixIncrement_Long {
    static void run() {

        long x = 5;
        long y = ++x;
        check(x == 6 && y == 6);
    }
}

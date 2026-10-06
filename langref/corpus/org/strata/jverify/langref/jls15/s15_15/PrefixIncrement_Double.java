package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment ++ applied to a double: adds 1.0 and yields
// the post-increment value.
class PrefixIncrement_Double {
    static void run() {

        double x = 1.5;
        double y = ++x;
        check(x == 2.5 && y == 2.5);
    }
}

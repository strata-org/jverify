package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment Operator ++: ++a adds one and yields the incremented value.
class PrefixIncrementOperator_1 {
    static void run() {

        int a = 5;
        ++a;              // a becomes 6, expression value is 6
        check(a == 6);
    }
}

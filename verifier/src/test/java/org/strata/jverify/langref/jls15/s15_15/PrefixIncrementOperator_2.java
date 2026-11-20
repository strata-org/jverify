package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.1 -- Prefix Increment Operator ++: ++a adds one and yields the incremented value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixIncrementOperator_2#run")
class PrefixIncrementOperator_2 {
    static void run() {

        int a = 5;
        int v = ++a;
        check(v == 6);
    }
}

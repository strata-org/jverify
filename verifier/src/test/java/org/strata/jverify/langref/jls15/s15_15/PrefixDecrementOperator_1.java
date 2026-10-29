package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.2 -- Prefix Decrement Operator --: --a subtracts one and yields the decremented value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixDecrementOperator_1#run")
class PrefixDecrementOperator_1 {
    static void run() {

        int a = 5;
        --a;              // a becomes 4, expression value is 4
        check(a == 4);
    }
}

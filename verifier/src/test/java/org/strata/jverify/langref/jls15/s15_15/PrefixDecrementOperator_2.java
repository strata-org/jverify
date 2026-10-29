package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.2 -- Prefix Decrement Operator --: --a subtracts one and yields the decremented value.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.PrefixDecrementOperator_2#run")
class PrefixDecrementOperator_2 {
    static void run() {

        int a = 5;
        int v = --a;
        check(v == 4);
    }
}

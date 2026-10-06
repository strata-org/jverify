package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.2 -- Prefix Decrement Operator --: --a subtracts one and yields the decremented value.
class PrefixDecrementOperator_2 {
    static void run() {

        int a = 5;
        int v = --a;
        check(v == 4);
    }
}

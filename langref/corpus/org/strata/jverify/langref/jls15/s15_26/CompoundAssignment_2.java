package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- Compound Assignment Operators (op=): E1 op= E2 is equivalent
// to E1 = (T)((E1) op (E2)) where T is the type of E1, with E1 evaluated once
// and an implicit narrowing cast applied to the result.
class CompoundAssignment_2 {
    static void run() {

        int x = 10;
        x += 5;

        x -= 3;
        check(x == 12);
    }
}

package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.3 -- Reference Conditional Expression: the type of an unrelated
// pair (Integer, String) is their least upper bound (Object); each branch's
// dynamic type is retained.
class ReferenceConditional_LUB {
    static void run() {

        Object o = true ? Integer.valueOf(1) : "x";
        check(o instanceof Integer);
    }
}

package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.3 -- Reference Conditional Expression: the type of an unrelated
// pair (Integer, String) is their least upper bound (Object); each branch's
// dynamic type is retained.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.ReferenceConditional_LUB#run")
class ReferenceConditional_LUB {
    static void run() {

        Object o = true ? Integer.valueOf(1) : "x";
        check(o instanceof Integer);
    }
}

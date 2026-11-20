package org.strata.jverify.langref.jls4.s4_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.12 -- Local Variable Declarations: 'var' infers a parameterized reference
// type from the initializer (JLS 14.4 / 14.4.1).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_12.Var_LocalArrayList#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Var_LocalArrayList {
    static void run() {

        var l = new java.util.ArrayList<Integer>();
        l.add(1);
        check(l.get(0) == 1);
    }
}

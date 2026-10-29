package org.strata.jverify.langref.jls15.s15_22;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.2 -- Boolean Logical Operators & ^ |: non-short-circuit logical AND, XOR, OR on boolean
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_22.BooleanLogicalOperators_10#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class BooleanLogicalOperators_10 {
    static void run() {

        boolean t = true;
        check((t | sideEffectFalse()) == true);
    }

    static boolean sideEffectTrue() {
        return true;
    }

    static boolean sideEffectFalse() {
        return false;
    }
}

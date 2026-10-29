package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a reference cast that fails the run-time type
// check throws ClassCastException.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_BadCastCCE#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastExpression_BadCastCCE {
    static void run() {

        Object o = "hi";
        boolean caught = false;
        try {
            Integer i = (Integer) o;
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);
    }
}

package org.strata.jverify.langref.jls15.s15_16;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: an intersection type cast
// (T1 & T2) constrains the target to implement all listed types; a lambda cast
// to (Runnable & Serializable) is serializable at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_16.CastExpression_IntersectionCast#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CastExpression_IntersectionCast {
    static void run() {

        Runnable r = (Runnable & java.io.Serializable) () -> {};
        check(r instanceof java.io.Serializable);
    }
}

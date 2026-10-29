package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: hexadecimal float literal with negative binary exponent and f suffix.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.FloatingPointLiterals_15#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointLiterals_15 {
    static void run() {

        float x = 0x1.0p-1f;
        check(x == 0.5f);
    }
}

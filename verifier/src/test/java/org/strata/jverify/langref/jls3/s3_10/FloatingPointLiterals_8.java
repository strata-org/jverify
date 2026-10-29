package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-Point Literals: double/float, exponent, hex float, suffixes, underscores
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.FloatingPointLiterals_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointLiterals_8 {
    static void run() {

        double hexFloat = 0x1.8p1;
        check(hexFloat == 3.0);
    }
}

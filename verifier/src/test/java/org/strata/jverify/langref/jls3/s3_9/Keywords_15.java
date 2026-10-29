package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the primitive type keywords 'byte', 'short', 'float', 'double'.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_15#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Keywords_15 {
    static void run() {

        byte b = 1;
        short s = 2;
        float f = 3.0f;
        double d = 4.0;
        check(b + s + f + d == 10.0);
    }
}

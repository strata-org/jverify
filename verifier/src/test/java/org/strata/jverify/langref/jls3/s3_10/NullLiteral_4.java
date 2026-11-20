package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.8 -- The Null Literal: null assignable to reference types, equality, assignment
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.NullLiteral_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NullLiteral_4 {
    static void run() {

        String s = null;
        s = "now set";
        check(s != null);
    }
}

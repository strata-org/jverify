package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the 'enum' keyword declares an enum type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_13#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Keywords_13 {
    static void run() {

        check(E_Keywords_13.A.ordinal() == 0);
    }
}

enum E_Keywords_13 { A, B }

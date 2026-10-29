package org.strata.jverify.langref.jls8.s8_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.2 -- an enum body may declare instance methods; each constant
// inherits them and may call the implicit ordinal()/name().
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_9.Enum_InstanceMethod#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Enum_InstanceMethod {
    enum E {
        A, B;

        int who() {
            return ordinal() + 1;
        }
    }

    static void run() {
        check(E.B.who() == 2);
    }
}

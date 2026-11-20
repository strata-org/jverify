package org.strata.jverify.langref.jls8.s8_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.9 -- an enum type may implement one or more interfaces; each constant
// then satisfies the interface contract.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_9.Enum_ImplementsInterface#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Enum_ImplementsInterface {
    interface Named {
        String label();
    }

    enum E implements Named {
        A, B;

        public String label() {
            return name().toLowerCase();
        }
    }

    static void run() {
        check(E.A.label().equals("a"));
    }
}

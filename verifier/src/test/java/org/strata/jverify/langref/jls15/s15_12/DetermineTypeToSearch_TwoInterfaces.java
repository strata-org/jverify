package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.1 -- Determine Type to Search: TypeName.super.m() searches the interface
// TypeName, so a class implementing two interfaces that each declare a default m
// reaches each default, and neither call dispatches on the receiver (JLS 15.12.4.4).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.DetermineTypeToSearch_TwoInterfaces#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class DetermineTypeToSearch_TwoInterfaces {
    interface I {
        default int m() {
            return 1;
        }
    }

    interface J {
        default int m() {
            return 2;
        }
    }

    static class C implements I, J {
        public int m() {
            return I.super.m() * 10 + J.super.m();
        }
    }

    static void run() {
        C c = new C();
        check(c.m() == 12);
    }
}

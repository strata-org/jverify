package org.strata.jverify.langref.jls15.s15_12;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.12.1 -- Determine Type to Search: TypeName.super.m() searches the interface
// TypeName. Here I's member k is the default it inherits: L's, which overrides K's (JLS 9.4.1).
// I lists K first, so a search that stops at the first default it finds returns K's.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_12.DetermineTypeToSearch_InheritedDefault#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class DetermineTypeToSearch_InheritedDefault {
    interface K {
        default int k() {
            return 1;
        }
    }

    interface L extends K {
        default int k() {
            return 2;
        }
    }

    interface I extends K, L {
    }

    static class D implements I {
        public int k() {
            return I.super.k() * 10;
        }
    }

    static void run() {
        D d = new D();
        check(d.k() == 20);
    }
}

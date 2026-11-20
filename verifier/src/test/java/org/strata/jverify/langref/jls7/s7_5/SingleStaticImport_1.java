package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import static java.lang.Math.PI;

// JLS 7.5.3 -- Single-Static-Import Declaration: imports a static member of a
// named type so it may be referred to by simple name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.SingleStaticImport_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SingleStaticImport_1 {
    static void run() {
        check(PI > 3.14 && PI < 3.15);
    }
}

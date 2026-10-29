package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import static java.lang.Math.max;

// JLS 7.5.3 -- Single-Static-Import Declaration: imports the named static
// method so it may be called by its simple name (`max` here).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.SingleStaticImport_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class SingleStaticImport_2 {
    static void run() {
        check(max(1, 2) == 2);
    }
}

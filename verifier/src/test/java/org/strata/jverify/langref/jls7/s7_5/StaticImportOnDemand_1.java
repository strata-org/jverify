package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import static java.lang.Math.*;

// JLS 7.5.4 -- Static-Import-on-Demand Declaration: imports all accessible
// static members of a named type so they may be referred to by simple name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.StaticImportOnDemand_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class StaticImportOnDemand_1 {
    static void run() {
        check(abs(-5) == 5);
    }
}

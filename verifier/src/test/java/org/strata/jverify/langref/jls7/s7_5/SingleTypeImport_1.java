package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import java.util.ArrayList;

// JLS 7.5.1 -- Single-Type-Import Declaration: imports a single named type so
// its simple name may be used within the compilation unit.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.SingleTypeImport_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SingleTypeImport_1 {
    static void run() {
        ArrayList<Integer> l = new ArrayList<>();
        l.add(1);
        check(l.get(0) == 1);
    }
}

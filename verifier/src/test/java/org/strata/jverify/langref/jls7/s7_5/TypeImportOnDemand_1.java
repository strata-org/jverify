package org.strata.jverify.langref.jls7.s7_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

import java.util.*;

// JLS 7.5.2 -- Type-Import-on-Demand Declaration: imports all accessible
// types of a named package so they may be referred to by simple name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls7.s7_5.TypeImportOnDemand_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TypeImportOnDemand_1 {
    static void run() {
        List<Integer> l = List.of(1, 2, 3);
        check(l.get(1) == 2);
    }
}

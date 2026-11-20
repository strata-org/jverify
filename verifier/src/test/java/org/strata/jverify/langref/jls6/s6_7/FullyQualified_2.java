package org.strata.jverify.langref.jls6.s6_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names: `java.util.ArrayList` may be referenced
// directly by its fully qualified name in place of an import declaration.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_7.FullyQualified_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FullyQualified_2 {
    static void run() {
        java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
        l.add(3);
        check(l.get(0) == 3);
    }
}

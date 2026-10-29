package org.strata.jverify.langref.jls6.s6_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names: a member type nested inside another type
// (here `java.util.Map.Entry`) can be referenced by its dotted fully
// qualified name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_7.FullyQualified_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FullyQualified_3 {
    static void run() {
        java.util.Map.Entry<Integer, String> e = java.util.Map.entry(1, "a");
        check(e.getKey() == 1);
    }
}

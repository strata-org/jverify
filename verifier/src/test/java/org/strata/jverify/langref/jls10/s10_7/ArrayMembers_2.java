package org.strata.jverify.langref.jls10.s10_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: the public final field 'length', element access,
// multidimensional length, and the cloned-array member behavior.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_7.ArrayMembers_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayMembers_2 {
    static void run() {

        // length reflects an array literal's component count.
        int[] b = {10, 20, 30};
        check(b.length == 3);
    }
}

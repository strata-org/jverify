package org.strata.jverify.langref.jls10.s10_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: the public final field 'length', element access,
// multidimensional length, and the cloned-array member behavior.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_7.ArrayMembers_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayMembers_3 {
    static void run() {

        // A zero-length array is permitted; length is 0.
        int[] empty = new int[0];
        check(empty.length == 0);
    }
}

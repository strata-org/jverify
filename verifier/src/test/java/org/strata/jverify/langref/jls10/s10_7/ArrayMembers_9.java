package org.strata.jverify.langref.jls10.s10_7;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: the public final field 'length', element access,
// multidimensional length, and the cloned-array member behavior.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_7.ArrayMembers_9#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayMembers_9 {
    static void run() {

        // Ragged (jagged) arrays: inner arrays may differ in length.
        int[][] ragged = new int[3][];
        ragged[0] = new int[1];
        ragged[1] = new int[2];
        ragged[2] = new int[3];
        check(ragged[1].length == 2);
    }
}

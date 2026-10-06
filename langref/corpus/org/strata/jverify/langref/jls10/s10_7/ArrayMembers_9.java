package org.strata.jverify.langref.jls10.s10_7;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: the public final field 'length', element access,
// multidimensional length, and the cloned-array member behavior.
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

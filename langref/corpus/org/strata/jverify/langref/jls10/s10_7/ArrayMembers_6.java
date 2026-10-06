package org.strata.jverify.langref.jls10.s10_7;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: the public final field 'length', element access,
// multidimensional length, and the cloned-array member behavior.
class ArrayMembers_6 {
    static void run() {

        // For a multidimensional array, length is the size of the outer dimension,
        // and each component is itself an array with its own length.
        int[][] m = new int[2][4];
        check(m[1].length == 4);
    }
}

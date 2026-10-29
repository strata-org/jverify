// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.6 -- Array Initializers: nested initializers with `new int[][]{...}`
// creating a ragged two-dimensional array.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_6.ArrayInitializers_NestedNewT#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayInitializers_NestedNewT {
    static void run() {
        int[][] m = new int[][]{{1, 2}, {3}};
        check(m[0][1] == 2 && m[1][0] == 3 && m[1].length == 1);
    }
}

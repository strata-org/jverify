// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.2 -- Enhanced for statement iterating over a two-dimensional
// array via nested for-each loops.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_14.EnhancedForMultiDim_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EnhancedForMultiDim_1 {
    static void run() {
        int[][] m = {{1, 2}, {3, 4}};
        int sum = 0;
        for (int[] row : m) {
            for (int v : row) {
                sum += v;
            }
        }
        check(sum == 10);
    }
}

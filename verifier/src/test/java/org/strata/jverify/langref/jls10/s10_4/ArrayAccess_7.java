// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.4 -- Array Access: an array access expression a[i] reads/writes a
// component; the array reference is evaluated before the index expression,
// indices are int values, and access is range-checked at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_4.ArrayAccess_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayAccess_7 {
    static void run() {
     // unassigned component holds the default value

        // Array reference may itself come from an expression (a[][]).
        int[][] m = new int[2][3];
        m[1][2] = 7;
        check(m[0][0] == 0);
    }
}

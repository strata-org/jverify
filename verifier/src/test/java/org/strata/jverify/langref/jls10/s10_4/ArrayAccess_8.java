// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.4 -- Array Access: an array access expression a[i] reads/writes a
// component; the array reference is evaluated before the index expression,
// indices are int values, and access is range-checked at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_4.ArrayAccess_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayAccess_8 {
    static void run() {

        int[] a = new int[5];
        // Write to components via array access expressions.
        a[0] = 10;
        a[1] = 20;
        int i = 2;
        a[i] = 30;            // index given by a variable of type int
        a[i + 1] = a[0] + a[1];

        // Index expression is evaluated and used as an int.
        long idx = 1L;
        check(a[(int) idx] == 20);
    }
}

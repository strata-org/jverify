// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: a final array reference is immutable, but the
// components of the referenced array are still mutable.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_2.ArrayVariables_FinalRefMutable#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayVariables_FinalRefMutable {
    static void run() {
        final int[] a = new int[]{1};
        a[0] = 99;
        check(a[0] == 99);
    }
}

// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.4 -- Array Access: indexing a null array reference throws
// NullPointerException.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_4.ArrayAccess_NullArray#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayAccess_NullArray {
    static void run() {
        int[] a = null;
        boolean caught = false;
        try {
            int x = a[0];
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}

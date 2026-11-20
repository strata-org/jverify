// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_14;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.2 -- Enhanced for statement over an Iterable (java.util.List).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_14.EnhancedForIterable_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class EnhancedForIterable_1 {
    static void run() {
        int sum = 0;
        for (Integer v : java.util.List.of(1, 2, 3)) {
            sum += v;
        }
        check(sum == 6);
    }
}

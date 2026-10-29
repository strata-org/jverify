// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: enum selector expression, case labels are
// unqualified enum constant names.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchOnEnum_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchOnEnum_1 {
    enum E { A, B; }

    static void run() {
        E e = E.B;
        int r;
        switch (e) {
            case A: r = 1; break;
            case B: r = 2; break;
            default: r = 0;
        }
        check(r == 2);
    }
}

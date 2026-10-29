// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: guarded pattern label using `when` clause.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchPattern_Guarded#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchPattern_Guarded {
    static void run() {
        Object o = 7;
        int r;
        switch (o) {
            case Integer i when i > 0 -> r = 1;
            case Integer i -> r = -1;
            default -> r = 0;
        }
        check(r == 1);
    }
}

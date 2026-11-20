// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: a `case null` label matches a null selector.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchPattern_NullLabel#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchPattern_NullLabel {
    static void run() {
        String s = null;
        int r;
        switch (s) {
            case null -> r = 0;
            case "x" -> r = 1;
            default -> r = -1;
        }
        check(r == 0);
    }
}

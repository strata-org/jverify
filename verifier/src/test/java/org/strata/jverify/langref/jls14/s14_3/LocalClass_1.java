// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a class declared inside a block.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_3.LocalClass_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LocalClass_1 {
    static void run() {
        class Local {
            int m() { return 42; }
        }
        check(new Local().m() == 42);
    }
}

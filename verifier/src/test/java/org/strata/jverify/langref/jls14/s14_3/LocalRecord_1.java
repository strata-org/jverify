// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a local record declared in a method.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_3.LocalRecord_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LocalRecord_1 {
    static void run() {
        record P(int x, int y) {}
        check(new P(1, 2).y() == 2);
    }
}

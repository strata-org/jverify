// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_19;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.19 -- Synchronized statement: evaluating a null lock expression
// throws NullPointerException before entering the block.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_19.Synchronized_NullNPE#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Synchronized_NullNPE {
    static void run() {
        Object lock = null;
        boolean caught = false;
        try {
            synchronized (lock) {}
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}

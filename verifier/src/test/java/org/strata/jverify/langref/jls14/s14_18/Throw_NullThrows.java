// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.18 -- Throw statement: throwing a null Throwable reference throws
// NullPointerException at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_18.Throw_NullThrows#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Throw_NullThrows {
    static void run() {
        RuntimeException e = null;
        boolean caught = false;
        try {
            throw e;
        } catch (NullPointerException npe) {
            caught = true;
        }
        check(caught);
    }
}

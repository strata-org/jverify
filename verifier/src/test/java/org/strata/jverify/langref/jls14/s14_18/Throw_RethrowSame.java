// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.18 -- Throw statement: rethrowing the same exception from a catch
// block propagates it outward.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_18.Throw_RethrowSame#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Throw_RethrowSame {
    static void run() {
        boolean caught = false;
        try {
            try {
                throw new RuntimeException("x");
            } catch (RuntimeException e) {
                throw e;
            }
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}

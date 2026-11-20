// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- Try-with-resources: a resource declared with `final` is
// closed exactly once when the try block completes normally.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_20.TryWithResources_FinalResource#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TryWithResources_FinalResource {
    static class R implements AutoCloseable {
        int closed = 0;
        public void close() { closed++; }
    }

    static void run() {
        R r = new R();
        try (final R resource = r) {}
        check(r.closed == 1);
    }
}

// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_20;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- Try-finally: a return in the finally block replaces a value
// that the try block was about to return.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_20.TryFinallyOverridesReturn_1#run")
class TryFinallyOverridesReturn_1 {
    static int m() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }

    static void run() {
        check(m() == 2);
    }
}
